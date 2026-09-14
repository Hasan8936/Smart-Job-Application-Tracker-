import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { useMemo, useRef, useEffect } from 'react'
import * as THREE from 'three'
import './aurora-ring.css'

// Mouse-tracking state shared between the event listener and the animation loop
const mouse = { x: 0, y: 0, tx: 0, ty: 0 }

function GlowRing() {
  const group = useRef()

  // 95 closely spaced light-strand curves forming the ring body
  const { geometries, materials } = useMemo(() => {
    const geos = []
    const mats = []
    const count = 95

    for (let i = 0; i < count; i++) {
      const offset = i * 0.018
      const points = []

      for (let j = 0; j <= 200; j++) {
        const t = (j / 200) * Math.PI * 2

        // Slight per-strand radius variation for the layered look
        const radius = 2.05 + Math.sin(t * 3 + offset * 8) * 0.025

        const x = Math.cos(t) * radius * (1 + Math.sin(offset * 4) * 0.015)
        const y = Math.sin(t) * radius * 0.82
        const z = Math.sin(t * 2 + offset * 10) * 0.22 + Math.cos(t * 3 + offset * 5) * 0.05

        points.push(new THREE.Vector3(x, y, z))
      }

      const curve = new THREE.CatmullRomCurve3(points, true)
      geos.push(new THREE.TubeGeometry(curve, 200, 0.0075 + (i % 5) * 0.0014, 4, true))

      // Spectral gradient: blue(0.58) → cyan(0.52) → green(0.38) → orange(0.08)
      // concentrated around the ring's circumference
      const hue = 0.58 - (i / count) * 0.50   // 0.58 → 0.08
      const sat = 1.0
      const lit = 0.52 + Math.sin(i * 0.4) * 0.06

      mats.push(new THREE.MeshBasicMaterial({
        color: new THREE.Color().setHSL(hue, sat, lit),
        transparent: true,
        opacity: 0.065 + Math.sin(i * 0.37) * 0.022,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
      }))
    }

    return { geometries: geos, materials: mats }
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      geometries.forEach(g => g.dispose())
      materials.forEach(m => m.dispose())
    }
  }, [geometries, materials])

  useFrame((state) => {
    if (!group.current) return
    const t = state.clock.elapsedTime

    // Smooth mouse-follow rotation
    mouse.tx += (mouse.x - mouse.tx) * 0.055
    mouse.ty += (mouse.y - mouse.ty) * 0.055

    group.current.rotation.y = t * 0.16 + mouse.tx * 0.45
    group.current.rotation.x = Math.sin(t * 0.35) * 0.12 + mouse.ty * 0.3
    group.current.rotation.z = Math.sin(t * 0.20) * 0.05
  })

  return (
    <group ref={group}>
      {geometries.map((geo, i) => (
        <mesh key={i} geometry={geo} material={materials[i]} />
      ))}
    </group>
  )
}

function EnergyParticles() {
  const pointsRef = useRef()
  const count = 900

  const { positions, colors } = useMemo(() => {
    const pos = new Float32Array(count * 3)
    const col = new Float32Array(count * 3)

    for (let i = 0; i < count; i++) {
      const angle = Math.random() * Math.PI * 2
      const radius = 1.68 + Math.random() * 0.72

      pos[i * 3]     = Math.cos(angle) * radius
      pos[i * 3 + 1] = Math.sin(angle) * radius * 0.82
      pos[i * 3 + 2] = (Math.random() - 0.5) * 0.28

      // Color matches the ring gradient: blue → cyan → green → orange
      const hue = 0.58 - (i / count) * 0.50
      const c = new THREE.Color().setHSL(hue, 1, 0.65)
      col[i * 3]     = c.r
      col[i * 3 + 1] = c.g
      col[i * 3 + 2] = c.b
    }

    return { positions: pos, colors: col }
  }, [])

  useFrame((state) => {
    if (!pointsRef.current) return
    const t = state.clock.elapsedTime
    pointsRef.current.rotation.y = t * 0.20 + mouse.tx * 0.45
    pointsRef.current.rotation.x = Math.sin(t * 0.3) * 0.1 + mouse.ty * 0.3
  })

  return (
    <points ref={pointsRef}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" count={count} array={positions} itemSize={3} />
        <bufferAttribute attach="attributes-color" count={count} array={colors} itemSize={3} />
      </bufferGeometry>
      <pointsMaterial
        size={0.019}
        vertexColors
        transparent
        opacity={0.85}
        blending={THREE.AdditiveBlending}
        depthWrite={false}
        sizeAttenuation
      />
    </points>
  )
}

// Bright accent dots that orbit on the inner ring edge
function OrbitingNodes() {
  const refs = [useRef(), useRef(), useRef()]

  const nodes = useMemo(() => [
    { speed: 1.0, radius: 2.05, ry: 0.82, phase: 0,              color: '#b8f0ff', size: 0.07 },
    { speed: 0.7, radius: 2.08, ry: 0.82, phase: Math.PI * 1.1,  color: '#7dffd4', size: 0.05 },
    { speed: 1.3, radius: 2.02, ry: 0.82, phase: Math.PI * 0.45, color: '#ffb347', size: 0.04 },
  ], [])

  useFrame((state) => {
    const t = state.clock.elapsedTime
    nodes.forEach((nd, i) => {
      const r = refs[i].current
      if (!r) return
      const angle = t * nd.speed * 0.36 + nd.phase
      r.position.set(
        Math.cos(angle) * nd.radius,
        Math.sin(angle) * nd.radius * nd.ry,
        Math.sin(angle * 2) * 0.18,
      )
      r.rotation.y = t * nd.speed * 0.36 + mouse.tx * 0.45
    })
  })

  return (
    <>
      {nodes.map((nd, i) => (
        <mesh key={i} ref={refs[i]}>
          <sphereGeometry args={[nd.size, 12, 12]} />
          <meshBasicMaterial
            color={nd.color}
            transparent
            opacity={0.92}
            blending={THREE.AdditiveBlending}
            depthWrite={false}
          />
        </mesh>
      ))}
    </>
  )
}

// Deep blue inner fill with subtle glow
function CoreGlow() {
  return (
    <mesh>
      <sphereGeometry args={[1.55, 32, 32]} />
      <meshBasicMaterial
        color="#03082e"
        transparent
        opacity={0.72}
        depthWrite={false}
        side={THREE.FrontSide}
      />
    </mesh>
  )
}

// Wide diffuse halo ring to fake bloom (additive blending, very fat strokes)
function GlowHalo() {
  const group = useRef()

  const { geometries, materials } = useMemo(() => {
    const geos = []
    const mats = []
    const passes = [
      { scale: 1.0,  tubeR: 0.18, opacity: 0.018 },
      { scale: 1.01, tubeR: 0.11, opacity: 0.032 },
      { scale: 1.02, tubeR: 0.06, opacity: 0.06  },
    ]

    for (const p of passes) {
      // Simple torus-like ellipse approximation via CatmullRom
      const pts = []
      for (let j = 0; j <= 120; j++) {
        const t = (j / 120) * Math.PI * 2
        pts.push(new THREE.Vector3(
          Math.cos(t) * 2.05 * p.scale,
          Math.sin(t) * 2.05 * 0.82 * p.scale,
          Math.sin(t * 2) * 0.1,
        ))
      }
      const curve = new THREE.CatmullRomCurve3(pts, true)
      geos.push(new THREE.TubeGeometry(curve, 120, p.tubeR, 6, true))
      mats.push(new THREE.MeshBasicMaterial({
        color: new THREE.Color(0.35, 0.75, 1.0),
        transparent: true,
        opacity: p.opacity,
        blending: THREE.AdditiveBlending,
        depthWrite: false,
        side: THREE.DoubleSide,
      }))
    }
    return { geometries: geos, materials: mats }
  }, [])

  useEffect(() => {
    return () => {
      geometries.forEach(g => g.dispose())
      materials.forEach(m => m.dispose())
    }
  }, [geometries, materials])

  useFrame((state) => {
    if (!group.current) return
    const t = state.clock.elapsedTime
    group.current.rotation.y = t * 0.16 + mouse.tx * 0.45
    group.current.rotation.x = Math.sin(t * 0.35) * 0.12 + mouse.ty * 0.3
  })

  return (
    <group ref={group}>
      {geometries.map((geo, i) => (
        <mesh key={i} geometry={geo} material={materials[i]} />
      ))}
    </group>
  )
}

function Scene() {
  const { gl } = useThree()

  useEffect(() => {
    const onMove = (e) => {
      const rect = gl.domElement.getBoundingClientRect()
      mouse.x = ((e.clientX - rect.left) / rect.width  - 0.5) * 2
      mouse.y = -((e.clientY - rect.top)  / rect.height - 0.5) * 2
    }
    gl.domElement.addEventListener('mousemove', onMove)
    return () => gl.domElement.removeEventListener('mousemove', onMove)
  }, [gl])

  return (
    <>
      <color attach="background" args={['#000000']} />
      <GlowHalo />
      <CoreGlow />
      <GlowRing />
      <EnergyParticles />
      <OrbitingNodes />
    </>
  )
}

export default function AuroraRing({ height = '500px', className = '' }) {
  return (
    <div
      className={`aurora-ring ${className}`}
      style={{ height }}
      aria-hidden="true"
    >
      <Canvas
        camera={{ position: [0, 0, 6], fov: 45 }}
        dpr={[1, 2]}
        gl={{ antialias: true, alpha: false, powerPreference: 'high-performance' }}
      >
        <Scene />
      </Canvas>
      <div className="aurora-vignette" />
    </div>
  )
}
