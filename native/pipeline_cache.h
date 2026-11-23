/* Device-local Vulkan cache. Called after device creation, and on the
 * compiler worker between jobs. A driver update invalidates the old blob. */
static VkPipelineCache fight_open_cache(VkDevice device,VkPhysicalDevice physical) {
    VkPipelineCache cache=VK_NULL_HANDLE;
    VkPipelineCacheCreateInfo ci={VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO};
    const char *path=getenv("FNR3_PIPELINE_CACHE");
    void *data=NULL;
    FILE *file=path?fopen(path,"rb"):NULL;
    if(file) {
        long size=-1;
        if(fseek(file,0,SEEK_END)==0)size=ftell(file);
        rewind(file);
        if(size>=(long)sizeof(VkPipelineCacheHeaderVersionOne) && size<=32*1024*1024 && (data=malloc(size))
                && fread(data,1,size,file)==(size_t)size) {
            VkPipelineCacheHeaderVersionOne header;
            VkPhysicalDeviceProperties props;
            memcpy(&header,data,sizeof header);
            vkGetPhysicalDeviceProperties(physical,&props);
            if(header.headerSize>=sizeof header && header.headerSize<=(size_t)size &&
               header.headerVersion==VK_PIPELINE_CACHE_HEADER_VERSION_ONE && header.vendorID==props.vendorID &&
               header.deviceID==props.deviceID && !memcmp(header.pipelineCacheUUID,props.pipelineCacheUUID,VK_UUID_SIZE)) {
                ci.pInitialData=data;ci.initialDataSize=size;
            }
        }
        fclose(file);
    }
    if(vkCreatePipelineCache(device,&ci,NULL,&cache)!=VK_SUCCESS && ci.initialDataSize) {
        ci.initialDataSize=0;ci.pInitialData=NULL;vkCreatePipelineCache(device,&ci,NULL,&cache);
    }
    free(data);
    return cache;
}
static void fight_save_cache(VkDevice device,VkPipelineCache cache) {
    const char *path=getenv("FNR3_PIPELINE_CACHE");
    if(!path || !cache)return;
    size_t size=0;
    if(vkGetPipelineCacheData(device,cache,&size,NULL)!=VK_SUCCESS || size<32 || size>32*1024*1024)return;
    void *data=malloc(size);
    if(!data)return;
    if(vkGetPipelineCacheData(device,cache,&size,data)==VK_SUCCESS) {
        char tmp[1024];
        int n=snprintf(tmp,sizeof tmp,"%s.tmp",path);
        if(n>0 && (size_t)n<sizeof tmp) {
            FILE *out=fopen(tmp,"wb");
            if(out) {
                int good=fwrite(data,1,size,out)==size;
                if(fclose(out)==0 && good){
#ifdef _WIN32
                    MoveFileExA(tmp,path,MOVEFILE_REPLACE_EXISTING);
#else
                    rename(tmp,path);
#endif
                }
            }
        }
    }
    free(data);
}
