export default function HeroCareerArtwork({ alt, className }) {
  return (
    <picture style={{ display: 'contents' }}>
      <source type="image/webp" srcSet="/hero-career-agents.webp" />
      <img
        src="/hero-career-agents.png"
        width={760}
        height={1140}
        alt={alt}
        decoding="async"
        fetchpriority="high"
        className={className}
      />
    </picture>
  )
}