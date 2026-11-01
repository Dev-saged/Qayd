"""يولّد هوية أيقونة قيد: ختم ذهبي مسنّن بحرف ق محفور. يكتب SVG الرئيسي وملفات Android."""
import math, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
C = 512

def rosette(r0=330, amp=5, lobes=72, steps=720):
    pts = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        r = r0 + amp * math.cos(lobes * t)
        pts.append((C + r * math.cos(t), C + r * math.sin(t)))
    return "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + " Z"

def circle(r, cx=C, cy=C):
    return f"M{cx + r:.1f},{cy:.1f} a{r:.1f},{r:.1f} 0 1,1 {-2 * r:.1f},0 a{r:.1f},{r:.1f} 0 1,1 {2 * r:.1f},0 Z"

# حرف ق: إحداثيات العلامة الأصلية (رأس، ساق، كأس) مكبّرة إلى داخل الختم
S, OX, OY = 0.47, 512 - 508 * 0.47, 524 - 514 * 0.47
def p(x, y): return f"{OX + x * S:.1f},{OY + y * S:.1f}"
QAF = (f"M{p(754, 400)} A{112 * S:.1f},{112 * S:.1f} 0 1,1 {p(530, 400)} A{112 * S:.1f},{112 * S:.1f} 0 1,1 {p(754, 400)} "
       f"M{p(754, 400)} L{p(754, 600)} A{246 * S:.1f},{246 * S:.1f} 0 0,1 {p(262, 600)}")
QAF_W = 92 * S
DOTS = circle(48 * S, OX + 582 * S, OY + 186 * S) + " " + circle(48 * S, OX + 702 * S, OY + 186 * S)

ROS = rosette()
RING = circle(300)
DISC = circle(268)
INNER = circle(276)
BEADS = " ".join(circle(7.5, C + 288 * math.cos(2 * math.pi * i / 48), C + 288 * math.sin(2 * math.pi * i / 48)) for i in range(48))

NAVY_A, NAVY_B = "#141A2C", "#06080F"
GOLD = ["#F6DC92", "#D9A846", "#8A5D1C"]
DISC_G = ["#E9C065", "#B98634", "#6E4815"]
INK = "#0B1020"

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" role="img" aria-label="قيد">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{NAVY_A}"/><stop offset="1" stop-color="{NAVY_B}"/></linearGradient>
    <radialGradient id="halo" cx="0.5" cy="0.5" r="0.5"><stop offset="0.55" stop-color="#D9A846" stop-opacity="0.18"/><stop offset="1" stop-color="#D9A846" stop-opacity="0"/></radialGradient>
    <linearGradient id="gold" x1="0.15" y1="0.1" x2="0.85" y2="0.95"><stop offset="0" stop-color="{GOLD[0]}"/><stop offset="0.5" stop-color="{GOLD[1]}"/><stop offset="1" stop-color="{GOLD[2]}"/></linearGradient>
    <linearGradient id="disc" x1="0.2" y1="0.1" x2="0.8" y2="0.95"><stop offset="0" stop-color="{DISC_G[0]}"/><stop offset="0.55" stop-color="{DISC_G[1]}"/><stop offset="1" stop-color="{DISC_G[2]}"/></linearGradient>
    <linearGradient id="shine" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFFFFF" stop-opacity="0.35"/><stop offset="0.45" stop-color="#FFFFFF" stop-opacity="0"/></linearGradient>
  </defs>
  <rect width="1024" height="1024" rx="232" fill="url(#bg)"/>
  <circle cx="512" cy="512" r="470" fill="url(#halo)"/>
  <path d="{ROS}" fill="url(#gold)"/>
  <path d="{RING}" fill="{GOLD[2]}"/>
  <path d="{RING}" fill="url(#gold)" opacity="0.55"/>
  <path d="{BEADS}" fill="{GOLD[0]}"/>
  <path d="{INNER}" fill="{GOLD[2]}"/>
  <path d="{DISC}" fill="url(#disc)"/>
  <path d="{DISC}" fill="url(#shine)"/>
  <path d="{QAF}" fill="none" stroke="{INK}" stroke-width="{QAF_W:.1f}" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="{DOTS}" fill="{INK}"/>
</svg>
'''
open(os.path.join(ROOT, "brand/qayd-mark.svg"), "w").write(svg)

RES = os.path.join(ROOT, "app/src/main/res/drawable")
GROUP = '<group android:scaleX="0.88" android:scaleY="0.88" android:pivotX="512" android:pivotY="512">'
def lg(x1, y1, x2, y2, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return (f'<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" '
            f'android:endX="{x2}" android:endY="{y2}">{items}</gradient></aapt:attr>')
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" '
        'android:viewportWidth="1024" android:viewportHeight="1024">\n')

fg = (HEAD + GROUP +
      f'<path android:pathData="{ROS}">{lg(250, 230, 790, 830, [(0, "#FFF6DC92"), (0.5, "#FFD9A846"), (1, "#FF8A5D1C")])}</path>'
      f'<path android:pathData="{RING}" android:fillColor="#FF8A5D1C"/>'
      f'<path android:pathData="{RING}">{lg(250, 230, 790, 830, [(0, "#8CF6DC92"), (0.5, "#8CD9A846"), (1, "#8C8A5D1C")])}</path>'
      f'<path android:pathData="{BEADS}" android:fillColor="#FFF6DC92"/>'
      f'<path android:pathData="{INNER}" android:fillColor="#FF8A5D1C"/>'
      f'<path android:pathData="{DISC}">{lg(300, 260, 720, 790, [(0, "#FFE9C065"), (0.55, "#FFB98634"), (1, "#FF6E4815")])}</path>'
      f'<path android:pathData="{DISC}">{lg(512, 246, 512, 500, [(0, "#59FFFFFF"), (1, "#00FFFFFF")])}</path>'
      f'<path android:pathData="{QAF}" android:strokeColor="#FF0B1020" android:strokeWidth="{QAF_W:.1f}" '
      'android:strokeLineCap="round" android:strokeLineJoin="round"/>'
      f'<path android:pathData="{DOTS}" android:fillColor="#FF0B1020"/>'
      '</group>\n</vector>\n')
open(os.path.join(RES, "ic_launcher_foreground.xml"), "w").write(fg)

bg = (HEAD +
      f'<path android:pathData="M0,0h1024v1024h-1024z">{lg(0, 0, 1024, 1024, [(0, "#FF141A2C"), (1, "#FF06080F")])}</path>'
      '<path android:pathData="' + circle(470) + '"><aapt:attr name="android:fillColor"><gradient android:type="radial" '
      'android:centerX="512" android:centerY="512" android:gradientRadius="470">'
      '<item android:offset="0.55" android:color="#2ED9A846"/><item android:offset="1" android:color="#00D9A846"/>'
      '</gradient></aapt:attr></path>\n</vector>\n')
open(os.path.join(RES, "ic_launcher_background.xml"), "w").write(bg)

mono = (HEAD + GROUP +
        f'<path android:pathData="{ROS} {DISC}" android:fillColor="#FFFFFFFF" android:fillType="evenOdd"/>'
        f'<path android:pathData="{QAF}" android:strokeColor="#FFFFFFFF" android:strokeWidth="{QAF_W:.1f}" '
        'android:strokeLineCap="round" android:strokeLineJoin="round"/>'
        f'<path android:pathData="{DOTS}" android:fillColor="#FFFFFFFF"/>'
        '</group>\n</vector>\n')
open(os.path.join(RES, "ic_launcher_monochrome.xml"), "w").write(mono)
print("ok")
