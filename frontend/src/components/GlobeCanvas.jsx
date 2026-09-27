import { useEffect, useRef } from 'react'
import * as THREE from 'three'

/* Three.js hero globe. Split out of LandingPage so the ~120 kB (gzip) three.js chunk loads after the
   hero text has painted instead of blocking it. */
export default function GlobeCanvas() {
  const mountRef = useRef(null)

  useEffect(() => {
    const el = mountRef.current
    if (!el) return

    const W = el.clientWidth, H = el.clientHeight
    const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.setSize(W, H)
    el.appendChild(renderer.domElement)

    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(45, W / H, 0.1, 100)
    camera.position.set(0, 0, 5.8)

    // Lights
    const amb = new THREE.AmbientLight(0x1a0533, 0.9)
    scene.add(amb)
    const d1 = new THREE.DirectionalLight(0xa78bfa, 1.4)
    d1.position.set(3, 5, 3)
    scene.add(d1)
    const d2 = new THREE.DirectionalLight(0x00f2fe, 0.5)
    d2.position.set(-4, -2, 2)
    scene.add(d2)

    // Core icosahedron
    const core = new THREE.Mesh(
      new THREE.IcosahedronGeometry(1.18, 2),
      new THREE.MeshPhongMaterial({
        color: 0x050115, emissive: 0x1a0542, specular: 0xa78bfa,
        shininess: 110, flatShading: true, transparent: true, opacity: 0.92
      })
    )
    scene.add(core)

    // Outer wireframe cage
    const cage = new THREE.Mesh(
      new THREE.IcosahedronGeometry(1.58, 2),
      new THREE.MeshBasicMaterial({ color: 0xa78bfa, wireframe: true, transparent: true, opacity: 0.32 })
    )
    scene.add(cage)

    // Inner cyan wire orb
    const inner = new THREE.Mesh(
      new THREE.SphereGeometry(0.62, 28, 28),
      new THREE.MeshBasicMaterial({ color: 0x00f2fe, wireframe: true, transparent: true, opacity: 0.18 })
    )
    scene.add(inner)

    // Torus rings helper
    function mkRing(radius, tube, rx, ry, color, opacity) {
      const m = new THREE.Mesh(
        new THREE.TorusGeometry(radius, tube, 14, 80),
        new THREE.MeshBasicMaterial({ color, transparent: true, opacity })
      )
      m.rotation.x = rx
      m.rotation.y = ry
      return m
    }
    const r1 = mkRing(2.05, 0.014, Math.PI / 3, 0.35, 0xa78bfa, 0.72)
    const r2 = mkRing(2.35, 0.012, -Math.PI / 4, 0.75, 0x7c3aed, 0.58)
    const r3 = mkRing(2.65, 0.009, Math.PI / 5, -0.6, 0x00f2fe, 0.45)
    scene.add(r1, r2, r3)

    // Orbiting nodes
    const nodeColors = [0xa78bfa, 0x7c3aed, 0x00f2fe, 0xa78bfa, 0x10b981,
      0xa78bfa, 0x00f2fe, 0x7c3aed, 0xa78bfa, 0x00f2fe,
      0x10b981, 0xa78bfa, 0x7c3aed, 0xa78bfa, 0x00f2fe]
    const nodeGroup = new THREE.Group()
    const nodeMeshes = []
    for (let i = 0; i < 15; i++) {
      const phi = Math.acos(-1 + (2 * i) / 15)
      const theta = Math.sqrt(15 * Math.PI) * phi
      const r = 2.05 + Math.random() * 0.6
      const node = new THREE.Mesh(
        new THREE.SphereGeometry(0.055, 8, 8),
        new THREE.MeshBasicMaterial({ color: nodeColors[i % nodeColors.length] })
      )
      node.position.setFromSphericalCoords(r, phi, theta)
      node.userData.baseR = r
      node.userData.phi = phi
      node.userData.theta = theta
      node.userData.speed = 0.3 + Math.random() * 0.4
      node.userData.offset = Math.random() * Math.PI * 2
      nodeMeshes.push(node)
      nodeGroup.add(node)
    }
    scene.add(nodeGroup)

    // Stars
    const starGeo = new THREE.BufferGeometry()
    const starPos = []
    for (let i = 0; i < 120; i++) {
      const phi = Math.random() * Math.PI * 2
      const cosTheta = Math.random() * 2 - 1
      const theta = Math.acos(cosTheta)
      const r = 3.8 + Math.random() * 1.8
      starPos.push(
        r * Math.sin(theta) * Math.cos(phi),
        r * Math.sin(theta) * Math.sin(phi),
        r * Math.cos(theta)
      )
    }
    starGeo.setAttribute('position', new THREE.Float32BufferAttribute(starPos, 3))
    const stars = new THREE.Points(
      starGeo,
      new THREE.PointsMaterial({ color: 0xa78bfa, size: 0.045, transparent: true, opacity: 0.7 })
    )
    scene.add(stars)

    // Mouse parallax
    let mx = 0, my = 0, tx = 0, ty = 0
    const onMouse = (e) => {
      mx = (e.clientX / window.innerWidth - 0.5) * 2
      my = (e.clientY / window.innerHeight - 0.5) * 2
    }
    window.addEventListener('mousemove', onMouse)

    // Resize
    const onResize = () => {
      const w = el.clientWidth, h = el.clientHeight
      renderer.setSize(w, h)
      camera.aspect = w / h
      camera.updateProjectionMatrix()
    }
    window.addEventListener('resize', onResize)

    // Animation loop — runs only while the canvas is on screen and the tab is visible.
    let rafId = null
    let onScreen = true
    const clock = new THREE.Clock()
    const animate = () => {
      rafId = requestAnimationFrame(animate)
      const t = clock.getElapsedTime()
      tx += (mx - tx) * 0.045
      ty += (my - ty) * 0.045

      core.rotation.y = t * 0.12
      core.rotation.x = t * 0.04 + ty * 0.3
      cage.rotation.y = -t * 0.09
      cage.rotation.x = t * 0.03
      inner.rotation.y = t * 0.18
      r1.rotation.z = t * 0.14
      r2.rotation.z = -t * 0.10
      r3.rotation.z = t * 0.08
      stars.rotation.y = t * 0.02

      nodeMeshes.forEach((n) => {
        const angle = n.userData.theta + t * n.userData.speed + n.userData.offset
        n.position.setFromSphericalCoords(n.userData.baseR, n.userData.phi, angle)
      })

      scene.rotation.y = tx * 0.18
      scene.rotation.x = -ty * 0.12

      renderer.render(scene, camera)
    }
    const sync = () => {
      const run = onScreen && !document.hidden
      if (run && rafId === null) animate()
      if (!run && rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
    }
    const io = new IntersectionObserver(([entry]) => { onScreen = entry.isIntersecting; sync() })
    io.observe(el)
    document.addEventListener('visibilitychange', sync)
    sync()

    return () => {
      io.disconnect()
      document.removeEventListener('visibilitychange', sync)
      if (rafId !== null) cancelAnimationFrame(rafId)
      window.removeEventListener('mousemove', onMouse)
      window.removeEventListener('resize', onResize)
      renderer.dispose()
      if (el.contains(renderer.domElement)) el.removeChild(renderer.domElement)
    }
  }, [])

  return <div ref={mountRef} style={{ position: 'absolute', inset: 0 }} />
}
