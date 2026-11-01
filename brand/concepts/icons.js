// ثلاث تنويعات لفكرة "ق الميزان" على لوحة 108 (المنطقة الآمنة 21..87)
const C = { ink: '#0E1322', deep: '#060911', gold: '#E4B24C', goldD: '#9C6B1C', goldL: '#F7DE9A', ivory: '#F3ECDC', paper: '#F4EEE1' };

const defs = (id) => `
<defs>
  <radialGradient id="bg${id}" cx=".32" cy=".22" r="1"><stop offset="0" stop-color="#1A2236"/><stop offset=".6" stop-color="${C.ink}"/><stop offset="1" stop-color="${C.deep}"/></radialGradient>
  <linearGradient id="g${id}" x1="0" y1="0" x2=".9" y2="1"><stop offset="0" stop-color="${C.goldL}"/><stop offset=".45" stop-color="${C.gold}"/><stop offset="1" stop-color="${C.goldD}"/></linearGradient>
  <linearGradient id="p${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FBF7EE"/><stop offset="1" stop-color="#EAE1CC"/></linearGradient>
</defs>`;

// أ — السطر: ق بخط واحد ذهبي يجلس على سطر القيد، النقطتان طرفا الحساب
const A = {
  name: 'أ — السطر',
  idea: 'ق بخط واحد متصل يجلس على سطر الدفتر. النقطتان طرفا الحساب: ذهبية "لي" وعاجية "علي".',
  bg: (id) => `<rect width="108" height="108" fill="url(#bg${id})"/>`,
  fg: (id, m) => `
  <g transform="translate(54 56) scale(.9) translate(-54 -56)">
    <g fill="none" stroke="${m || `url(#g${id})`}" stroke-width="7" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="62" cy="44" r="8.5"/>
      <path d="M70.5 44 V58 A18 18 0 0 1 34.5 58 V53"/>
    </g>
    <rect x="25" y="83" width="58" height="3.6" rx="1.8" fill="${m || `url(#g${id})`}" opacity="${m ? 1 : .9}"/>
    <circle cx="56" cy="26.5" r="4.6" fill="${m || C.gold}"/>
    <circle cx="68.5" cy="26.5" r="4.6" fill="${m || C.ivory}"/>
  </g>`,
};

// ب — الكوفي: ق بالخط الكوفي المربع على ورق الدفتر، النقطتان معيّنان كنقاط الخط العربي الأصيل
const B = {
  name: 'ب — الكوفي',
  idea: 'ق بالكوفي المربع، مبنية على شبكة، على لون ورق الدفتر. النقطتان معيّنان كما في الخط الأصيل.',
  bg: (id) => `<rect width="108" height="108" fill="url(#p${id})"/>`,
  fg: (id, m) => `
  <g transform="translate(54 57) scale(.92) translate(-54 -57)">
    <path fill-rule="evenodd" fill="${m || C.ink}" d="
      M54 36 H75 V75 H30 V54 H39 V66 H66 V57 H54 Z
      M62 43 V50 H68 V43 Z"/>
    <path d="M57 20.5 L62.5 26 L57 31.5 L51.5 26 Z" fill="${m || C.gold}"/>
    <path d="M70 20.5 L75.5 26 L70 31.5 L64.5 26 Z" fill="${m || C.ink}"/>
    <rect x="24" y="81" width="60" height="4" fill="${m || C.gold}"/>
  </g>`,
};

// ج — الأسطر: الحرف نفسه مكوّن من أسطر الدفتر، وآخر سطر يمتد ليكون سطر القيد
const Cc = {
  name: 'ج — الأسطر',
  idea: 'الحرف مرسوم من أسطر الدفتر نفسها، وآخر سطر يخرج من الحرف ويمتد ليكون سطر القيد.',
  bg: (id) => `<rect width="108" height="108" fill="url(#bg${id})"/>`,
  fg: (id, m) => {
    let rows = '';
    for (let y = 30; y < 84; y += 5.4) rows += `<rect x="0" y="${y.toFixed(1)}" width="108" height="3.4"/>`;
    return `
  <defs><mask id="k${id}" maskUnits="userSpaceOnUse" x="0" y="0" width="108" height="108">
    <g fill="none" stroke="#fff" stroke-width="13" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="62" cy="45" r="6.4"/>
      <path d="M68.4 45 V58 A16.7 17.5 0 0 1 35 58 V54"/>
    </g>
  </mask></defs>
  <g transform="translate(54 56) scale(.92) translate(-54 -56)">
    <g mask="url(#k${id})" fill="${m || `url(#g${id})`}">${rows}</g>
    <rect x="22" y="83.2" width="64" height="3.4" rx="1.7" fill="${m || `url(#g${id})`}"/>
    <circle cx="56" cy="25.5" r="4.6" fill="${m || C.gold}"/>
    <circle cx="68.5" cy="25.5" r="4.6" fill="${m || C.ivory}"/>
  </g>`;
  },
};

const VARIANTS = [A, B, Cc];
let seq = 0;
function icon(v, mono) {
  const id = ++seq;
  return `<svg viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">${defs(id)}${mono ? `<rect width="108" height="108" fill="${mono.bg}"/>` : v.bg(id)}${v.fg(id, mono && mono.fg)}</svg>`;
}
if (typeof module !== 'undefined') module.exports = { VARIANTS, icon };

// تطويرات الكوفي
const Q = `M54 36 H75 V75 H30 V54 H39 V66 H66 V57 H54 Z M62 43 V50 H68 V43 Z`;
const dia = (x, f) => `<path d="M${x} 20.5 L${x + 5.5} 26 L${x} 31.5 L${x - 5.5} 26 Z" fill="${f}"/>`;
const wrap = (s) => `<g transform="translate(54 57) scale(.92) translate(-54 -57)">${s}</g>`;
const grid = (col, op) => { let s = ''; for (let i = 0; i <= 108; i += 9) s += `<path d="M${i} 0V108M0 ${i}H108" stroke="${col}" stroke-width=".35" opacity="${op}"/>`; return s; };

const B1 = {
  name: 'ب١ — الذهب على الحبر',
  idea: 'الحرف ذهبي مصقول على حبر داكن، وشبكة الكوفي التي بُني عليها تظهر خفيفة في الخلفية كأنها مخطط المهندس.',
  bg: (id) => `<rect width="108" height="108" fill="url(#bg${id})"/>${grid('#E4B24C', .10)}`,
  fg: (id, m) => wrap(`
    ${m ? '' : `<path fill-rule="evenodd" d="${Q}" fill="#000" opacity=".45" transform="translate(1.2 1.8)"/>`}
    <path fill-rule="evenodd" d="${Q}" fill="${m || `url(#g${id})`}"/>
    ${m ? '' : `<path d="M54 36 H75 V37.2 H55.2 V57 H54 Z" fill="#FFF3C8" opacity=".55"/>`}
    ${dia(57, m || `url(#g${id})`)}${dia(70, m || C.ivory)}
    <rect x="24" y="81" width="60" height="3" fill="${m || C.ivory}" opacity="${m ? 1 : .85}"/>`),
};

const B2 = {
  name: 'ب٢ — صفحة الدفتر',
  idea: 'الورق صار صفحة دفتر حقيقية: أسطر خفيفة وهامش ذهبي مزدوج على اليمين، والحرف بالحبر بعمق خفيف كأنه مطبوع.',
  bg: (id) => {
    let r = ''; for (let y = 18; y < 108; y += 9) r += `<path d="M0 ${y}H108" stroke="#C9B994" stroke-width=".45" opacity=".55"/>`;
    return `<rect width="108" height="108" fill="url(#p${id})"/>${r}<path d="M88 0V108M90.2 0V108" stroke="${C.gold}" stroke-width=".7"/>`;
  },
  fg: (id, m) => wrap(`
    ${m ? '' : `<path fill-rule="evenodd" d="${Q}" fill="#7A5A1E" opacity=".22" transform="translate(1 1.6)"/>`}
    <path fill-rule="evenodd" d="${Q}" fill="${m || C.ink}"/>
    ${dia(57, m || `url(#g${id})`)}${dia(70, m || C.ink)}
    <rect x="24" y="81" width="60" height="4" fill="${m || `url(#g${id})`}"/>`),
};

const B3 = {
  name: 'ب٣ — النقش',
  idea: 'لوح ذهبي والحرف محفور فيه بعمق كنقش على بلاطة، مستوحى من الكوفي المعماري. النقطتان بالحبر والعاج.',
  bg: (id) => `<rect width="108" height="108" fill="url(#g${id})"/><rect width="108" height="108" fill="url(#bg${id})" opacity=".0"/>
    <radialGradient id="v${id}" cx=".5" cy=".45" r=".75"><stop offset=".55" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#5A3A0C" stop-opacity=".35"/></radialGradient>
    <rect width="108" height="108" fill="url(#v${id})"/>`,
  fg: (id, m) => wrap(`
    ${m ? '' : `<path fill-rule="evenodd" d="${Q}" fill="#FFF0C2" opacity=".7" transform="translate(.9 1.3)"/>`}
    <path fill-rule="evenodd" d="${Q}" fill="${m || C.ink}"/>
    ${m ? '' : `<path d="M54 36 H75 V38.4 H56.4 V57 H54 Z M30 54 H39 V56.4 H32.4 V75 H30 Z" fill="#000" opacity=".55"/>`}
    ${dia(57, m || C.ink)}${dia(70, m || C.ivory)}
    <rect x="24" y="81" width="60" height="3.4" fill="${m || C.ink}"/>`),
};
const VARIANTS2 = [B1, B2, B3];
if (typeof module !== 'undefined') module.exports.VARIANTS2 = VARIANTS2;
