/* One device per game process. Record an image copy on the existing Vulkan
 * submission. Publication follows the same fence as the guest's 480p read. */
#include <vulkan/vulkan_android.h>
#include "android_gpu_socket.h"
#include <fcntl.h>
typedef struct {AHardwareBuffer *buffer; VkImage image; VkDeviceMemory memory; int foreign;} FightExternalImage;
static struct {
    VkDevice dev; VkPhysicalDevice phys; int available, listener, peer, ready, check;
    uint32_t width,height,generation; _Atomic uint32_t latest;
    int control_fd,pending;
    FightGpuMailbox *control;
    FightExternalImage slots[GPU_SLOTS];
} display={.listener=-1,.peer=-1,.control_fd=-1,.pending=-1};

static pthread_mutex_t gpu_pool_lock=PTHREAD_MUTEX_INITIALIZER;
static void gpu_drop_pool(void) {
    if(display.peer>=0) close(display.peer);
    /* Disconnection and renegotiation may overlap the preceding submission.
     * Wait only when retiring a published pool, never on an ordinary flip. */
    if(display.ready)vkDeviceWaitIdle(display.dev);
    display.peer=-1;display.ready=0;display.pending=-1;
    for(int i=0;i<GPU_SLOTS;i++) {
        FightExternalImage *im=&display.slots[i];
        if(im->image)vkDestroyImage(display.dev,im->image,NULL);
        if(im->memory)vkFreeMemory(display.dev,im->memory,NULL);
        if(im->buffer)AHardwareBuffer_release(im->buffer);
        memset(im,0,sizeof *im);
    }
    if(display.control)munmap(display.control,sizeof *display.control);
    if(display.control_fd>=0)close(display.control_fd);
    display.control_fd=-1;display.control=NULL;
}
static void gpu_display_close(void) {
    pthread_mutex_lock(&gpu_pool_lock);gpu_drop_pool();pthread_mutex_unlock(&gpu_pool_lock);if(display.listener>=0)close(display.listener);
    display.listener=-1;display.available=0;
}
static int gpu_image(FightExternalImage *im,uint32_t w,uint32_t h) {
    AHardwareBuffer_Desc desc={.width=w,.height=h,.layers=1,.format=AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
        .usage=AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE|AHARDWAREBUFFER_USAGE_GPU_COLOR_OUTPUT};
    if(!AHardwareBuffer_isSupported(&desc) || AHardwareBuffer_allocate(&desc,&im->buffer))return 0;
    PFN_vkGetAndroidHardwareBufferPropertiesANDROID properties=(PFN_vkGetAndroidHardwareBufferPropertiesANDROID)
        vkGetDeviceProcAddr(display.dev,"vkGetAndroidHardwareBufferPropertiesANDROID");
    VkAndroidHardwareBufferPropertiesANDROID prop={.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID};
    VkAndroidHardwareBufferFormatPropertiesANDROID fmt={.sType=VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_FORMAT_PROPERTIES_ANDROID};
    prop.pNext=&fmt;
    if(!properties || properties(display.dev,im->buffer,&prop)!=VK_SUCCESS || fmt.format!=VK_FORMAT_R8G8B8A8_UNORM)return 0;
    VkExternalMemoryImageCreateInfo ext={.sType=VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
        .handleTypes=VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID};
    VkImageCreateInfo info={.sType=VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO,.pNext=&ext,.imageType=VK_IMAGE_TYPE_2D,
        .format=fmt.format,.extent={w,h,1},.mipLevels=1,.arrayLayers=1,.samples=VK_SAMPLE_COUNT_1_BIT,
        .tiling=VK_IMAGE_TILING_OPTIMAL,.usage=VK_IMAGE_USAGE_TRANSFER_DST_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT};
    if(vkCreateImage(display.dev,&info,NULL,&im->image)!=VK_SUCCESS)return 0;
    VkMemoryDedicatedAllocateInfo dedicated={.sType=VK_STRUCTURE_TYPE_MEMORY_DEDICATED_ALLOCATE_INFO,.image=im->image};
    VkImportAndroidHardwareBufferInfoANDROID import={.sType=VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID,.pNext=&dedicated,.buffer=im->buffer};
    if(!prop.memoryTypeBits)return 0;
    VkMemoryAllocateInfo alloc={.sType=VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,.pNext=&import,.allocationSize=prop.allocationSize,
        .memoryTypeIndex=(uint32_t)__builtin_ctz(prop.memoryTypeBits)};
    return vkAllocateMemory(display.dev,&alloc,NULL,&im->memory)==VK_SUCCESS &&
        vkBindImageMemory(display.dev,im->image,im->memory,0)==VK_SUCCESS;
}
static void gpu_display_init(VkDevice dev,VkPhysicalDevice phys,int extensions) {
    const char *requested=getenv("FNR3_SHARED_FRAMES");
    if((!requested || strcmp(requested,"1")) && !gpu_display_requested())return;
    display.dev=dev;display.phys=phys;
    const char *path=getenv("FNR3_FRAME_PATH");
    if(!extensions || !path || getenv("FNR3_LEGACY_SCANOUT"))return;
    struct sockaddr_un addr;socklen_t n=gpu_address(&addr,path);
    int fd=socket(AF_UNIX,SOCK_SEQPACKET|SOCK_CLOEXEC|SOCK_NONBLOCK,0);
    if(fd<0)return;
    if(bind(fd,(struct sockaddr*)&addr,n) || listen(fd,1)){close(fd);return;}
    display.listener=fd;display.available=1;
}
static int gpu_prepare(uint32_t w,uint32_t h) {
    if(!display.available)return 0;
    if(display.peer>=0 && (w!=display.width || h!=display.height))
        gpu_drop_pool();
    if(display.peer<0){
        if(display.check++%24)return 0;
        int fd=accept4(display.listener,NULL,NULL,SOCK_CLOEXEC|SOCK_NONBLOCK);
        if(fd<0)return 0;
        if(!gpu_peer(fd)){close(fd);return 0;}
        display.peer=fd;display.width=w;display.height=h;
        display.generation=display.generation%8190+1;
        display.control_fd=ASharedMemory_create("FNR3 image ownership",sizeof(FightGpuMailbox));
        if(display.control_fd<0)goto fail;
        void *map=mmap(NULL,sizeof(FightGpuMailbox),PROT_READ|PROT_WRITE,MAP_SHARED,display.control_fd,0);
        if(map==MAP_FAILED)goto fail;
        display.control=map;
        for(int i=0;i<GPU_SLOTS;i++){
            atomic_init(&display.control->state[i],0);
            if(!gpu_image(&display.slots[i],w,h))goto fail;
        }
        FightGpuHello hello={FIGHT_GPU_MAGIC,w,h,display.generation};
        if(!gpu_hello(fd,&hello,&display.control_fd,1))goto fail;
        for(int i=0;i<GPU_SLOTS;i++)if(AHardwareBuffer_sendHandleToUnixSocket(display.slots[i].buffer,fd))goto fail;
        return 0;
    }
    if(!display.ready || !(++display.check%60)){
        char signal;int n=recv(display.peer,&signal,1,MSG_DONTWAIT);
        if(n==0 || (n<0 && errno!=EAGAIN && errno!=EWOULDBLOCK && errno!=EINTR))goto fail;
        if(n==1){
            if(signal!='Y')goto fail;
            display.ready=1;fprintf(stderr,"[Fight Night] Android shared GPU images %ux%u\n",w,h);
        }
    }
    return display.ready && w==display.width && h==display.height;
fail:
    gpu_drop_pool();return 0;
}
static int gpu_copy_display(VkCommandBuffer cmd,VkImage src,uint32_t qfam,uint32_t w,uint32_t h) {
    pthread_mutex_lock(&gpu_pool_lock);int ready=gpu_prepare(w,h);pthread_mutex_unlock(&gpu_pool_lock);
    if(!ready)return 0;
    int i=gpu_reserve(display.control,display.latest);
    if(i<0)return 0;
    FightExternalImage *im=&display.slots[i];
    VkImageMemoryBarrier b={.sType=VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,.dstAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT,
        .oldLayout=VK_IMAGE_LAYOUT_UNDEFINED,.newLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
        .srcQueueFamilyIndex=im->foreign?VK_QUEUE_FAMILY_FOREIGN_EXT:VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex=im->foreign?qfam:VK_QUEUE_FAMILY_IGNORED,.image=im->image,
        .subresourceRange={VK_IMAGE_ASPECT_COLOR_BIT,0,1,0,1}};
    vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,VK_PIPELINE_STAGE_TRANSFER_BIT,0,0,NULL,0,NULL,1,&b);
    VkImageCopy copy={.srcSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1},.dstSubresource={VK_IMAGE_ASPECT_COLOR_BIT,0,0,1},.extent={w,h,1}};
    /* Copy compatible four-byte texels. Both GL paths use the same BGRA swizzle. */
    vkCmdCopyImage(cmd,src,VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,im->image,VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,1,&copy);
    b.srcAccessMask=VK_ACCESS_TRANSFER_WRITE_BIT;b.dstAccessMask=0;b.oldLayout=VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    b.newLayout=VK_IMAGE_LAYOUT_GENERAL;b.srcQueueFamilyIndex=qfam;b.dstQueueFamilyIndex=VK_QUEUE_FAMILY_FOREIGN_EXT;
    vkCmdPipelineBarrier(cmd,VK_PIPELINE_STAGE_TRANSFER_BIT,VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,0,0,NULL,0,NULL,1,&b);
    im->foreign=1;display.pending=i;return 1;
}
unsigned fight_shared_take(void) {
    int i=display.pending;display.pending=-1;
    return i<0?0:(display.generation<<2)|(i+1);
}
void fight_shared_finish(unsigned token,uint32_t seq) {
    if(!token)return;
    pthread_mutex_lock(&gpu_pool_lock);
    if(token>>2!=display.generation || !display.control){pthread_mutex_unlock(&gpu_pool_lock);return;}
    int i=(token&3)-1;
    if(seq){gpu_publish(display.control,i,seq);display.latest=seq;}
    else atomic_store(&display.control->state[i],GPU_FREE);
    pthread_mutex_unlock(&gpu_pool_lock);
}

