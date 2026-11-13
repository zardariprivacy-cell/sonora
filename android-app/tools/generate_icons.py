from PIL import Image, ImageDraw
import math, os

S = 960  # master canvas
def lerp(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))

def master(round_icon=False):
    img = Image.new('RGBA',(S,S),(0,0,0,0))
    d = ImageDraw.Draw(img)
    # background: vertical purple gradient (drawn line by line), rounded or circle mask
    top=(32,4,64); bot=(13,2,24)
    bg = Image.new('RGBA',(S,S))
    bd = ImageDraw.Draw(bg)
    for y in range(S):
        bd.line([(0,y),(S,y)], fill=lerp(top,bot,y/S)+(255,))
    mask = Image.new('L',(S,S),0)
    md = ImageDraw.Draw(mask)
    if round_icon: md.ellipse([0,0,S,S], fill=255)
    else: md.rounded_rectangle([0,0,S,S], radius=int(S*0.22), fill=255)
    img.paste(bg,(0,0),mask)
    d = ImageDraw.Draw(img)
    cx, cy = S/2, S*0.52
    violet=(168,85,247,255); magenta=(232,121,249,255); deep=(124,58,237,255)
    # headband: thick arc
    band_box=[S*0.20,S*0.16,S*0.80,S*0.76]
    for wdt,col in [(46,magenta),(34,violet)]:
        d.arc(band_box, start=180, end=360, fill=col, width=wdt)
    # equalizer bars inside
    bars=[(0.40,0.52),(0.47,0.40),(0.54,0.58),(0.61,0.44)]
    for fx,h in bars:
        x=S*fx; y0=S*0.66; y1=S*0.66-S*h*0.5
        d.rounded_rectangle([x-13,y1,x+13,y0], radius=13, fill=magenta)
    # ear cups
    for fx in (0.20,0.80):
        x=S*fx
        d.rounded_rectangle([x-52,S*0.52,x+52,S*0.80], radius=44, fill=violet)
        d.rounded_rectangle([x-30,S*0.58,x+30,S*0.74], radius=24, fill=deep)
    return img

res = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app/src/main/res')
sizes={'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
for name,px in sizes.items():
    folder=os.path.join(res,f'mipmap-{name}')
    os.makedirs(folder,exist_ok=True)
    master(False).resize((px,px), Image.LANCZOS).save(os.path.join(folder,'ic_launcher.png'))
    master(True).resize((px,px), Image.LANCZOS).save(os.path.join(folder,'ic_launcher_round.png'))
os.makedirs(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'),exist_ok=True)
master(False).resize((512,512), Image.LANCZOS).save(os.path.expanduser('sonora-icon-512.png'))
print('icons done')
