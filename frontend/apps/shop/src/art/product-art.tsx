import { useId } from "react"

type Paint = (ids: { print: string }) => React.ReactNode

const SEAM = { fill: "none", stroke: "#1b1916", strokeOpacity: 0.35, strokeWidth: 2, strokeDasharray: "5 4" }
const INK = "#1b1916"

const coffee: Paint = ({ print }) => (
  <>
    <path d="M58 70c-6 40-10 92-4 128 2 12 14 18 66 18s64-6 66-18c6-36 2-88-4-128-24-8-100-8-124 0z" fill="#b98a52" />
    <path d="M58 70c-6 40-10 92-4 128 2 12 14 18 66 18s64-6 66-18c6-36 2-88-4-128-24-8-100-8-124 0z" fill={`url(#${print})`} />
    <path d="M52 64c22-12 114-12 136 0l-6 14c-26-9-98-9-124 0z" fill="#8d6234" />
    <path d="M86 48c6 6 10 12 10 18M154 48c-6 6-10 12-10 18" stroke="#6d4a26" strokeWidth="5" strokeLinecap="round" fill="none" />
    <path d="M92 44c14-8 42-8 56 0" stroke="#6d4a26" strokeWidth="5" strokeLinecap="round" fill="none" />
    <rect x="78" y="108" width="84" height="62" rx="4" fill="#f1efe8" transform="rotate(-3 120 139)" />
    <text x="120" y="132" textAnchor="middle" fontFamily="Bricolage Grotesque Variable, sans-serif" fontWeight="800" fontSize="17" fill={INK} transform="rotate(-3 120 139)">THIOTTE</text>
    <text x="120" y="152" textAnchor="middle" fontFamily="Inter Variable, sans-serif" fontWeight="600" fontSize="10" fill={INK} transform="rotate(-3 120 139)">1 450 m · 1 kg</text>
    <circle cx="150" cy="166" r="12" fill="#c8352e" transform="rotate(-3 120 139)" />
    <path d="M64 92c40 6 72 6 112 0M62 190c40 6 76 6 116 0" {...SEAM} />
  </>
)

const cacao: Paint = ({ print }) => (
  <>
    <path d="M120 34c40 14 62 56 58 100-4 46-30 76-58 80-28-4-54-34-58-80-4-44 18-86 58-100z" fill="#b8452f" />
    <path d="M120 34c40 14 62 56 58 100-4 46-30 76-58 80-28-4-54-34-58-80-4-44 18-86 58-100z" fill={`url(#${print})`} />
    <path d="M120 38c-6 50-6 120 0 172M96 46c-12 52-10 110 4 158M144 46c12 52 10 110-4 158" stroke="#7d2a1a" strokeWidth="4" fill="none" strokeLinecap="round" />
    <path d="M118 34c-4-10 2-20 12-24" stroke="#4d6b2a" strokeWidth="6" strokeLinecap="round" fill="none" />
    <ellipse cx="174" cy="196" rx="16" ry="11" fill="#5a3320" transform="rotate(-20 174 196)" />
    <ellipse cx="66" cy="198" rx="14" ry="10" fill="#6b3d26" transform="rotate(18 66 198)" />
    <path d="M66 130c30 8 78 8 108 0" {...SEAM} />
  </>
)

const chocolate: Paint = ({ print }) => (
  <>
    <rect x="46" y="70" width="140" height="70" rx="5" fill="#5a3320" transform="rotate(-8 116 105)" />
    <rect x="58" y="112" width="140" height="70" rx="5" fill="#6e3f27" transform="rotate(5 128 147)" />
    <g transform="rotate(5 128 147)">
      <path d="M86 112v70M114 112v70M142 112v70M170 112v70M58 147h140" stroke="#4a2918" strokeWidth="3" />
    </g>
    <rect x="44" y="150" width="152" height="54" rx="4" fill="#d93d74" transform="rotate(-2 120 177)" />
    <rect x="44" y="150" width="152" height="54" rx="4" fill={`url(#${print})`} transform="rotate(-2 120 177)" />
    <text x="120" y="184" textAnchor="middle" fontFamily="Bricolage Grotesque Variable, sans-serif" fontWeight="800" fontSize="22" fill="#fff0f5" transform="rotate(-2 120 177)">70 %</text>
    <path d="M50 166c46 4 96 2 140-4" {...SEAM} />
  </>
)

const mangoes: Paint = ({ print }) => (
  <>
    <path d="M52 148h136l-10 62H62z" fill="#a87b45" />
    <path d="M52 148h136l-10 62H62z" fill={`url(#${print})`} />
    <path d="M58 168h124M60 188h120" stroke="#7a5530" strokeWidth="4" />
    <path d="M70 150c-10-34 10-62 40-62s44 30 32 62z" fill="#f3c21a" />
    <path d="M112 150c-6-40 20-70 50-64s36 40 18 64z" fill="#f08a24" />
    <path d="M150 92c-10 8-22 24-24 40" stroke="#fff3c4" strokeOpacity="0.6" strokeWidth="6" strokeLinecap="round" fill="none" />
    <path d="M96 92c4-12 14-18 26-18" stroke="#2c8a4b" strokeWidth="5" strokeLinecap="round" fill="none" />
    <path d="M124 74c10-8 24-8 32-2-10 8-22 8-32 2z" fill="#2c8a4b" />
    <path d="M66 158c38 4 70 4 108 0" {...SEAM} />
  </>
)

const rum: Paint = ({ print }) => (
  <>
    <path d="M104 30h32v40c0 10 26 22 26 48v86c0 8-6 12-14 12H92c-8 0-14-4-14-12v-86c0-26 26-38 26-48z" fill="#7a3d12" />
    <path d="M104 30h32v40c0 10 26 22 26 48v86c0 8-6 12-14 12H92c-8 0-14-4-14-12v-86c0-26 26-38 26-48z" fill={`url(#${print})`} />
    <rect x="100" y="22" width="40" height="16" rx="3" fill="#1b1916" />
    <path d="M86 128h68v54H86z" fill="#1d6bb0" />
    <text x="120" y="152" textAnchor="middle" fontFamily="Bricolage Grotesque Variable, sans-serif" fontWeight="800" fontSize="15" fill="#e9f3fc">8 ANS</text>
    <text x="120" y="170" textAnchor="middle" fontFamily="Inter Variable, sans-serif" fontWeight="600" fontSize="9" fill="#e9f3fc">CANNE · 70 cl</text>
    <path d="M100 82c-8 10-12 22-12 36" stroke="#f6c58a" strokeOpacity="0.55" strokeWidth="6" strokeLinecap="round" fill="none" />
    <path d="M86 196h68" {...SEAM} />
  </>
)

const jar = (body: string, lid: string, label: string, labelInk: string, word: string): Paint =>
  function Jar({ print }) {
    return (
      <>
        <path d="M70 84h100c8 0 12 6 12 14v100c0 10-6 16-16 16H74c-10 0-16-6-16-16V98c0-8 4-14 12-14z" fill={body} />
        <path d="M70 84h100c8 0 12 6 12 14v100c0 10-6 16-16 16H74c-10 0-16-6-16-16V98c0-8 4-14 12-14z" fill={`url(#${print})`} />
        <rect x="64" y="56" width="112" height="30" rx="5" fill={lid} />
        <path d="M72 66h96M72 76h96" stroke={INK} strokeOpacity="0.25" strokeWidth="2" />
        <rect x="70" y="124" width="100" height="52" rx="3" fill={label} transform="rotate(-2 120 150)" />
        <text x="120" y="157" textAnchor="middle" fontFamily="Bricolage Grotesque Variable, sans-serif" fontWeight="800" fontSize="20" fill={labelInk} transform="rotate(-2 120 150)">{word}</text>
        <path d="M80 100c-6 30-6 70 0 96" stroke="#ffffff" strokeOpacity="0.35" strokeWidth="7" strokeLinecap="round" fill="none" />
        <path d="M62 110c40 4 76 4 116 0" {...SEAM} />
      </>
    )
  }

const epis = jar("#2c8a4b", "#f3c21a", "#f1efe8", INK, "ÉPIS")
const pikliz = jar("#e25b2b", "#1d6bb0", "#f3c21a", INK, "PIKLIZ")
const honey = jar("#e7a21c", "#5a3320", "#d93d74", "#fff0f5", "MIEL")

const ART: Record<string, Paint> = { coffee, cacao, chocolate, mangoes, rum, epis, pikliz, honey }

export function ProductArt({ product, className, title }: { product: string; className?: string; title: string }) {
  const id = useId().replaceAll(":", "")
  const paint = ART[product] ?? coffee
  const ids = { print: `print-${id}` }
  return (
    <svg viewBox="0 0 240 240" className={className} role="img" aria-label={title}>
      <defs>
        <pattern id={ids.print} width="18" height="9" patternUnits="userSpaceOnUse" patternTransform="rotate(-8)">
          <rect width="11" height="1.6" y="2" fill="#1b1916" opacity="0.13" />
          <rect width="6" height="1.6" x="12" y="2" fill="#1b1916" opacity="0.1" />
          <rect width="15" height="1.6" y="6.5" fill="#1b1916" opacity="0.08" />
        </pattern>
      </defs>
      <ellipse cx="120" cy="222" rx="78" ry="9" fill="#1b1916" opacity="0.18" />
      {paint(ids)}
    </svg>
  )
}
