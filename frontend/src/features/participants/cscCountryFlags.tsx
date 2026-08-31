export const CSC_COUNTRY_FLAG_CODES = ['XE', 'XN', 'XL', 'XS', 'XW'] as const

export type CscCountryFlagCode = typeof CSC_COUNTRY_FLAG_CODES[number]

export function isCscCountryFlagCode(code?: string | null): code is CscCountryFlagCode {
  return code !== undefined && code !== null && (CSC_COUNTRY_FLAG_CODES as readonly string[]).includes(code)
}

export function CscCountryFlag({ code, label, size }: { code: CscCountryFlagCode, label: string, size: number }) {
  const style = { display: 'inline-block', height: Math.round(size * 2 / 3), verticalAlign: 'middle', width: size }

  switch (code) {
    case 'XE':
      return <svg aria-label={label} data-csc-country="XE" role="img" style={style} viewBox="0 0 3 2">
        <rect fill="#fff" height="2" width="3" />
        <rect fill="#CE1124" height="0.4" width="3" y="0.8" />
        <rect fill="#CE1124" height="2" width="0.4" x="1.3" />
      </svg>
    case 'XN':
      return <svg aria-label={label} data-csc-country="XN" role="img" style={style} viewBox="0 0 3 2">
        <rect fill="#fff" height="2" width="3" />
        <rect fill="#C8102E" height="0.34" width="3" y="0.83" />
        <rect fill="#C8102E" height="2" width="0.34" x="1.33" />
        <path d="M1.39 .48 1.46 .34 1.52 .46 1.6 .32 1.65 .49 1.77 .4 1.72 .58H1.3L1.25 .4Z" fill="#F2C300" stroke="#222" strokeWidth=".025" />
        <polygon fill="#fff" points="1.5,.55 1.62,.78 1.88,.76 1.73,.98 1.88,1.2 1.62,1.18 1.5,1.43 1.38,1.18 1.12,1.2 1.27,.98 1.12,.76 1.38,.78" stroke="#222" strokeWidth=".035" />
        <path d="M1.43 .83v.23l-.07-.12-.06.03.08.19.02.17.1.08.1-.07.04-.17.08-.18-.06-.03-.07.12V.84h-.06v.18-.22h-.06v.22-.2Z" fill="#C8102E" />
      </svg>
    case 'XL':
      return <svg aria-label={label} data-csc-country="XL" role="img" style={style} viewBox="0 0 3 2">
        <rect fill="#000" height="0.667" width="3" />
        <rect fill="#DD0000" height="0.666" width="3" y="0.667" />
        <rect fill="#FFCE00" height="0.667" width="3" y="1.333" />
        <path d="M1.05 .45H1.95V1.22c0 .34-.2.59-.45.72-.25-.13-.45-.38-.45-.72Z" fill="#fff" stroke="#fff" strokeWidth=".08" />
        <path d="M1.1 .5H1.5V1H1.1Z" fill="#176FC1" />
        <path d="M1.5 .5H1.9V1H1.5Z" fill="#fff" />
        <path d="M1.1 1H1.5v.2c0 .27.13.47.2.54-.07.06-.13.1-.2.14-.22-.12-.4-.34-.4-.66Z" fill="#F2C300" />
        <path d="M1.5 1H1.9v.22c0 .32-.18.54-.4.66Z" fill="#F2C300" />
        <path d="M1.57 .57v.36M1.51 .7h.12" stroke="#C8102E" strokeWidth=".07" />
        <path d="M1.12 1.3 1.48 1.05" stroke="#C8102E" strokeWidth=".08" />
        <path d="M1.22 .84c.08-.19.18-.25.28-.14-.13.02-.16.13-.04.21-.12-.01-.19-.03-.24-.07Z" fill="#fff" />
        <path d="M1.57 1.55c.08-.24.16-.36.27-.35-.11.06-.13.18-.04.28-.07-.01-.15.02-.23.07Z" fill="#111" />
        <path d="M1.05 .45H1.95V1.22c0 .34-.2.59-.45.72-.25-.13-.45-.38-.45-.72Z" fill="none" stroke="#222" strokeWidth=".035" />
      </svg>
    case 'XS':
      return <svg aria-label={label} data-csc-country="XS" role="img" style={style} viewBox="0 0 3 2">
        <rect fill="#0065BD" height="2" width="3" />
        <path d="M0 0 L3 2 M3 0 L0 2" fill="none" stroke="#fff" strokeWidth="0.34" />
      </svg>
    case 'XW':
      return <svg aria-label={label} data-csc-country="XW" role="img" style={style} viewBox="0 0 3 2">
        <rect fill="#fff" height="1" width="3" />
        <rect fill="#00AB39" height="1" width="3" y="1" />
        <path d="M.25 1.43c.24-.23.46-.34.73-.37L.76 .72l.34.14.18-.33.19.35.34-.18-.14.35c.22-.02.4-.09.56-.22l-.12-.16.32.04.14-.17.12.2.2.04-.16.14.16.14-.3.03c-.09.2-.28.31-.52.31l.17.28-.25-.03-.18-.25-.24.02-.11.3-.25-.01.12-.29c-.28.04-.52.14-.72.3l-.29-.04.16-.15-.17-.1Z" fill="#C8102E" />
        <path d="M2.42 .72 2.54 .61 2.66 .67 2.55 .75Z" fill="#C8102E" />
      </svg>
  }
}
