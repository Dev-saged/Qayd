"""يولّد أيقونة قيد المعتمدة (ب٢ صفحة الدفتر): طبقات الأيقونة المتكيّفة + شعار داخل التطبيق + SVG رئيسي."""
import pathlib
R = pathlib.Path(__file__).resolve().parents[2]
D = R / 'app/src/main/res/drawable'
INK, GOLD, GOLD_L, GOLD_D, MARGIN = '#FF0E1322', '#FFE4B24C', '#FFF7DE9A', '#FF9C6B1C', '#FFE4B24C'
Q = 'M54,36 H75 V75 H30 V54 H39 V66 H66 V57 H54 Z M62,43 V50 H68 V43 Z'
DIA = lambda x: f'M{x},20.5 L{x+5.5},26 L{x},31.5 L{x-5.5},26 Z'
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
def grad(x1, y1, x2, y2, stops):
    items = ''.join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return f'<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}">{items}</gradient></aapt:attr>'
GOLD_G = lambda: grad(24, 20, 84, 86, [(0, GOLD_L), (0.45, GOLD), (1, GOLD_D)])

def background():
    s = f'<path android:pathData="M0,0h108v108h-108z">{grad(0,0,0,108,[(0,"#FFFBF7EE"),(1,"#FFEAE1CC")])}</path>\n'
    for y in range(18, 108, 9):
        s += f'<path android:pathData="M0,{y}H108" android:strokeColor="#8CC9B994" android:strokeWidth="0.45"/>\n'
    s += f'<path android:pathData="M88,0V108M90.2,0V108" android:strokeColor="{MARGIN}" android:strokeWidth="0.7"/>\n'
    return s

def mark(mono=None):
    g = '<group android:pivotX="54" android:pivotY="57" android:scaleX="0.92" android:scaleY="0.92">\n'
    if mono:
        g += f'<path android:fillType="evenOdd" android:fillColor="{mono}" android:pathData="{Q}"/>\n'
        g += f'<path android:fillColor="{mono}" android:pathData="{DIA(57)} {DIA(70)} M24,81 H84 V85 H24 Z"/>\n'
    else:
        g += f'<group android:translateX="1" android:translateY="1.6"><path android:fillType="evenOdd" android:fillColor="#387A5A1E" android:pathData="{Q}"/></group>\n'
        g += f'<path android:fillType="evenOdd" android:fillColor="{INK}" android:pathData="{Q}"/>\n'
        g += f'<path android:pathData="{DIA(57)}">{GOLD_G()}</path>\n'
        g += f'<path android:fillColor="{INK}" android:pathData="{DIA(70)}"/>\n'
        g += f'<path android:pathData="M24,81 H84 V85 H24 Z">{GOLD_G()}</path>\n'
    return g + '</group>\n'

(D / 'ic_launcher_background.xml').write_text(HEAD + background() + '</vector>\n')
(D / 'ic_launcher_foreground.xml').write_text(HEAD + mark() + '</vector>\n')
(D / 'ic_launcher_monochrome.xml').write_text(HEAD + mark('#FFFFFFFF') + '</vector>\n')
# شعار كامل مقصوص بزوايا ناعمة لاستخدامه داخل التطبيق
(D / 'qayd_logo.xml').write_text(HEAD + '<clip-path android:pathData="M24,0 H84 A24,24 0 0 1 108,24 V84 A24,24 0 0 1 84,108 H24 A24,24 0 0 1 0,84 V24 A24,24 0 0 1 24,0 Z"/>\n' + background() + mark() + '</vector>\n')
