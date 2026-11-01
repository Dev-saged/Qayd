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
