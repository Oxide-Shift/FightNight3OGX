"""Build the original BC2 backdrop for the Thor loading movie. Requires Pillow."""
from pathlib import Path
import math
import struct
from PIL import Image, ImageDraw


def backdrop():
    # Draw in the loading movie's 854 x 480 coordinates. The disc stretches the
    # background sprite to that stage. Interactive text is drawn separately.
    scale = 2
    image = Image.new("RGB", (854 * scale, 480 * scale), "#211829")
    draw = ImageDraw.Draw(image)

    def xy(points):
        return tuple(round(x * scale) for x in points)

    def line(points, color, width=1):
        draw.line(xy(points), fill=color, width=round(width * scale))

    def box(bounds, radius, fill, outline=None):
        draw.rounded_rectangle(xy(bounds), round(radius * scale), fill, outline, width=2)

    def oval(bounds, fill, outline=None):
        draw.ellipse(xy(bounds), fill, outline, width=2)

    # Ring ropes and corner post frame the play area outside the safe text margins.
    for edge in (0, 854):
        sign = 1 if edge == 0 else -1
        for y in (105, 212, 319):
            line((edge, y+42, edge+sign*109, y), "#49364e", 3)
            line((edge, y+45, edge+sign*109, y+3), "#1a151e", 2)
        line((edge+sign*104, 78, edge+sign*104, 365), "#453148", 5)
    box((123, 90, 731, 390), 6, "#1f1b24", "#514153")
    line((431, 110, 431, 373), "#493e4b")
    for x1, x2, y in ((138,413,122),(138,413,228),(446,715,122),(446,715,266)):
        line((x1,y,x2,y),"#584551")
        line((x1,y,x1+25,y),"#f1b666",2)
    for y in (128,157,186,215,276,304,332,360):
        box((446,y-1,538 if y>=276 else 481,y+18),4,"#332934","#745c64")
    for y in (132,169,359):
        box((136,y-1,201,y+18),4,"#302537","#695475")
    line((128,398,726,398),"#584353")
    for cx in (253,439):
        for x,y in ((cx,414),(cx+16,429),(cx,444),(cx-16,429)):
            oval((x-9,y-9,x+9,y+9),"#352e3d","#796979")

    # Front elevation from the AYN Thor manual. Two panels share the hinge.
    # Draw the leaders to the outside of their keys so the controls stay visible.
    shell, edge, bezel = "#685276","#b5a2c6","#18151d"
    box((581,2,656,42),4,shell,edge)
    box((584,4,653,40),2,bezel)
    box((588,6,649,38),1,"#242332")
    box((581,43,656,86),4,shell,edge)
    box((589,42,648,45),1,"#3b3047","#977fa8")
    box((599,47,638,83),2,bezel,"#a28aae")
    box((601,49,636,80),1,"#242332")
    for x,y in ((591,54),(646,71)):
        oval((x-5,y-5,x+5,y+5),"#28232e","#bfa8d1")
        oval((x-3,y-3,x+3,y+3),"#494151")
    line((591,67,591,76),bezel,3)
    line((586.5,71.5,595.5,71.5),bezel,3)
    for x,y in ((646,49.5),(641.7,54),(650.3,54),(646,58.5)):
        oval((x-1.9,y-1.9,x+1.9,y+1.9),"#241d26","#d3baad")
    for x in (590.5,646):
        oval((x-1.6,45.8,x+1.6,49),"#211b28","#c3b1ca")
        box((x-3,80,x+3,82),1,"#3e3447")
    line((559,49,570,49,578,54,585,54),"#b29dc5")
    line((559,72,585,72),"#b29dc5")
    line((648,47,657,47,667,32,677,32),"#d1ae80")
    line((653,54,675,54),"#d1ae80")
    line((652,71,675,71),"#d1ae80")
    return image.resize((512,256),Image.Resampling.LANCZOS)


def bc2(image):
    def pack(pixel):
        r,g,b=pixel
        return (r >> 3) << 11 | (g >> 2) << 5 | b >> 3

    def unpack(c):
        return ((c >> 11)*255//31, ((c >> 5)&63)*255//63, (c&31)*255//31)

    output=bytearray()
    for y in range(0,image.height,4):
        for x in range(0,image.width,4):
            colors=list(image.crop((x,y,x+4,y+4)).getdata())
            hi=pack(max(colors,key=sum));lo=pack(min(colors,key=sum))
            a,b=unpack(hi),unpack(lo)
            palette=[a,b,tuple((2*u+v)//3 for u,v in zip(a,b)),tuple((u+2*v)//3 for u,v in zip(a,b))]
            bits=0
            for i,pixel in enumerate(colors):
                best=min(range(4),key=lambda j:math.dist(pixel,palette[j]))
                bits |= best << (2*i)
            output.extend(struct.pack("<QHHI",0xffffffffffffffff,hi,lo,bits))
    return output


if __name__=="__main__":
    target=Path(__file__).resolve().parents[1]/"android/assets/thor-guide.dxt3"
    target.write_bytes(bc2(backdrop()))
