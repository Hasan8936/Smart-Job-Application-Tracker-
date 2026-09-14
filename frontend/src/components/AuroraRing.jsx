import { useRef, useEffect } from 'react'
import * as THREE from 'three'
import './aurora-ring.css'

// ── Spring physics ────────────────────────────────────────────────────────────
class Spring2D {
  constructor(k = 0.065, d = 0.82) {
    this.x = this.y = this.vx = this.vy = this.tx = this.ty = 0
    this.k = k; this.d = d
  }
  to(x, y) { this.tx = x; this.ty = y }
  step() {
    this.vx = this.vx * this.d + (this.tx - this.x) * this.k
    this.vy = this.vy * this.d + (this.ty - this.y) * this.k
    this.x += this.vx; this.y += this.vy
  }
}
class Spring1D {
  constructor(k = 0.10, d = 0.78) { this.v = this.c = this.t = 0; this.k = k; this.d = d }
  to(t) { this.t = t }
  step() { this.v = this.v * this.d + (this.t - this.c) * this.k; this.c += this.v; return this.c }
}

// ── GLSL ─────────────────────────────────────────────────────────────────────
const SIMPLEX = `
vec3 _m3(vec3 x){return x-floor(x*(1./289.))*289.;}
vec4 _m4(vec4 x){return x-floor(x*(1./289.))*289.;}
vec4 _p(vec4 x){return _m4(((x*34.)+1.)*x);}
vec4 _ti(vec4 r){return 1.79284291400159-.85373472095314*r;}
float snoise(vec3 v){
  const vec2 C=vec2(1./6.,1./3.); const vec4 D=vec4(0.,.5,1.,2.);
  vec3 i=floor(v+dot(v,C.yyy)); vec3 x0=v-i+dot(i,C.xxx);
  vec3 g=step(x0.yzx,x0.xyz); vec3 l=1.-g;
  vec3 i1=min(g.xyz,l.zxy); vec3 i2=max(g.xyz,l.zxy);
  vec3 x1=x0-i1+C.xxx; vec3 x2=x0-i2+C.yyy; vec3 x3=x0-D.yyy;
  i=_m3(i);
  vec4 pp=_p(_p(_p(i.z+vec4(0.,i1.z,i2.z,1.))+i.y+vec4(0.,i1.y,i2.y,1.))+i.x+vec4(0.,i1.x,i2.x,1.));
  float n_=.142857142857; vec3 ns=n_*D.wyz-D.xzx;
  vec4 j=pp-49.*floor(pp*ns.z*ns.z);
  vec4 x_=floor(j*ns.z); vec4 y_=floor(j-7.*x_);
  vec4 xx=x_*ns.x+ns.yyyy; vec4 yy=y_*ns.x+ns.yyyy; vec4 h=1.-abs(xx)-abs(yy);
  vec4 b0=vec4(xx.xy,yy.xy); vec4 b1=vec4(xx.zw,yy.zw);
  vec4 s0=floor(b0)*2.+1.; vec4 s1=floor(b1)*2.+1.; vec4 sh=-step(h,vec4(0.));
  vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy; vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;
  vec3 p0=vec3(a0.xy,h.x); vec3 p1=vec3(a0.zw,h.y); vec3 p2=vec3(a1.xy,h.z); vec3 p3=vec3(a1.zw,h.w);
  vec4 norm=_ti(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
  p0*=norm.x;p1*=norm.y;p2*=norm.z;p3*=norm.w;
  vec4 m=max(.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.); m=m*m;
  return 42.*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
}`

const VNOISE = `
float _h3(vec3 p){p=fract(p*vec3(443.8975,397.2973,491.1871));p+=dot(p,p.yzx+19.19);return fract(p.x*p.y*p.z);}
float vnoise(vec3 p){
  vec3 i=floor(p);vec3 f=fract(p);f=f*f*(3.-2.*f);
  return mix(mix(mix(_h3(i),_h3(i+vec3(1,0,0)),f.x),mix(_h3(i+vec3(0,1,0)),_h3(i+vec3(1,1,0)),f.x),f.y),
             mix(mix(_h3(i+vec3(0,0,1)),_h3(i+vec3(1,0,1)),f.x),mix(_h3(i+vec3(0,1,1)),_h3(i+vec3(1,1,1)),f.x),f.y),f.z);
}`

const VERT = `${SIMPLEX}
uniform float uTime;
varying vec3 vN, vWP, vLP;
void main(){
  vec3 pos=position;
  float n1=snoise(pos*1.45+vec3(uTime*.28,uTime*.17,0.))*.15;
  float n2=snoise(pos*3.1 -vec3(0.,uTime*.23,uTime*.18))*.075;
  float n3=snoise(pos*6.5 +vec3(uTime*.14,0.,uTime*.10))*.028;
  pos+=normal*(n1+n2+n3);
  vLP=pos;
  vN=normalize(normalMatrix*normal);
  vec4 wp=modelMatrix*vec4(pos,1.); vWP=wp.xyz;
  gl_Position=projectionMatrix*modelViewMatrix*vec4(pos,1.);
}`

const INNER_FRAG = `${VNOISE}
uniform float uTime;
varying vec3 vN, vWP, vLP;
void main(){
  vec3 N=normalize(vN);
  vec3 V=normalize(cameraPosition-vWP);
  float NdV=clamp(dot(N,V),0.,1.);
  vec3 nb=vLP*2.6;
  float n1=vnoise(nb+vec3(uTime*.11,-uTime*.08,0.));
  float n2=vnoise(nb*2.3+vec3(0.,uTime*.15,-uTime*.10))*.5;
  float n3=vnoise(nb*4.8-vec3(uTime*.07,0.,uTime*.06))*.25;
  float nebula=n1*.55+n2*.30+n3*.15;
  vec3 voidC=vec3(.010,.002,.032);
  vec3 nebC =vec3(.080,.018,.240);
  vec3 glowC=vec3(.200,.055,.560);
  vec3 sparkC=vec3(.440,.160,.900);
  vec3 col=mix(voidC,nebC,nebula);
  col=mix(col,glowC,pow(nebula,2.2)*.85);
  col=mix(col,sparkC,pow(nebula,5.0)*1.2);
  col*=pow(NdV,.28)+.05;
  float flicker=sin(uTime*3.+nebula*12.)*.5+.5;
  float sparkle=pow(max(nebula-.68,0.)*4.,5.)*NdV*flicker;
  col+=vec3(.50,.18,.95)*sparkle*.8;
  float alpha=clamp(.08+NdV*.86,0.,1.);
  gl_FragColor=vec4(col,alpha);
}`

const OUTER_FRAG = `
uniform float uTime;
varying vec3 vN, vWP, vLP;
void main(){
  vec3 N=normalize(vN);
  vec3 V=normalize(cameraPosition-vWP);
  float NdV=clamp(dot(N,V),0.,1.);
  float f  =pow(1.-NdV,2.6);
  float fe =pow(1.-NdV,5.5);
  vec3 col=vec3(.008,.002,.020);
  float rg=vLP.y*.40+.50;
  vec3 rA=vec3(.22,.42,1.00);
  vec3 rB=vec3(.62,.16,1.00);
  vec3 rC=vec3(.90,.78,1.00);
  vec3 rim=mix(rA,rB,rg); rim=mix(rim,rC,fe*.60);
  col+=rim*f*2.9;
  vec3 L1=normalize(vec3(1.6,1.4,.95));
  float s1=pow(max(dot(reflect(-L1,N),V),0.),20.);
  col+=vec3(.96,.82,1.00)*s1*1.7;
  vec3 L2=normalize(vec3(-.90,.25,.85));
  float s2=pow(max(dot(reflect(-L2,N),V),0.),46.);
  col+=vec3(.34,.58,1.00)*s2*1.05;
  float gs=sin(uTime*.54+vLP.y*3.2-vLP.x*2.5)*.5+.5;
  col+=vec3(.68,.48,1.00)*pow(gs,4.5)*f*.58;
  col+=vec3(.94,.86,1.00)*fe*3.5;
  float alpha=clamp(.022+f*.90,0.,1.);
  gl_FragColor=vec4(col,alpha);
}`

const HALO_FRAG = `
uniform float uTime;
varying vec3 vN, vWP;
void main(){
  vec3 V=normalize(cameraPosition-vWP);
  float rim=1.-clamp(dot(normalize(vN),V),0.,1.);
  rim=pow(rim,1.7);
  float pulse=.85+sin(uTime*.64)*.15;
  vec3 col=mix(vec3(.18,.07,.88),vec3(.52,.14,.98),rim);
  gl_FragColor=vec4(col*pulse,rim*.38);
}`

// ── Component ─────────────────────────────────────────────────────────────────
export default function AuroraRing({ height = '100vh', className = '' }) {
  const canvasRef = useRef(null)
  const containerRef = useRef(null)

  useEffect(() => {
    const canvas = canvasRef.current
    const container = containerRef.current
    if (!canvas || !container) return

    const W = () => container.clientWidth
    const H = () => container.clientHeight
    const MOBILE = W() < 560
    const TABLET = W() < 900

    // Scene + camera + renderer
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(46, W() / H(), 0.1, 100)
    camera.position.z = 5.2
    const renderer = new THREE.WebGLRenderer({ canvas, antialias: !MOBILE, alpha: true })
    renderer.setPixelRatio(Math.min(devicePixelRatio, MOBILE ? 1.5 : 2))
    renderer.setSize(W(), H())
    renderer.setClearColor(0x000000, 0)

    // Shared sphere geometry (inner + outer reuse it)
    const SEG = MOBILE ? 40 : (TABLET ? 62 : 86)
    const sharedGeo = new THREE.SphereGeometry(1.28, SEG, SEG)

    // Background radial glow plane
    const glowMesh = new THREE.Mesh(
      new THREE.PlaneGeometry(5.0, 5.0),
      new THREE.ShaderMaterial({
        uniforms: { uTime: { value: 0 } },
        vertexShader: `varying vec2 vUv;void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
        fragmentShader: `varying vec2 vUv;uniform float uTime;void main(){vec2 uv=vUv*2.-1.;float d=length(uv);float p=.93+sin(uTime*.54)*.07;float g=exp(-d*d*1.85*p);g=pow(g,1.3);vec3 col=mix(vec3(.40,.10,.90),vec3(.18,.04,.50),d);gl_FragColor=vec4(col,g*.62);}`,
        transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
      })
    )
    glowMesh.position.z = -0.8
    glowMesh.renderOrder = 0
    scene.add(glowMesh)

    // Inner void nebula sphere
    const innerMesh = new THREE.Mesh(sharedGeo, new THREE.ShaderMaterial({
      vertexShader: VERT, fragmentShader: INNER_FRAG,
      uniforms: { uTime: { value: 0 } },
      transparent: true, depthWrite: false,
    }))
    innerMesh.renderOrder = 1
    scene.add(innerMesh)

    // Outer glass shell
    const blobMat = new THREE.ShaderMaterial({
      vertexShader: VERT, fragmentShader: OUTER_FRAG,
      uniforms: { uTime: { value: 0 } },
      transparent: true, depthWrite: false,
    })
    const blob = new THREE.Mesh(sharedGeo, blobMat)
    blob.renderOrder = 2
    scene.add(blob)

    // Halo bloom
    const haloMat = new THREE.ShaderMaterial({
      vertexShader: `varying vec3 vN,vWP;void main(){vN=normalize(normalMatrix*normal);vec4 wp=modelMatrix*vec4(position,1.);vWP=wp.xyz;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
      fragmentShader: HALO_FRAG,
      uniforms: { uTime: { value: 0 } },
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    })
    const halo = new THREE.Mesh(new THREE.SphereGeometry(1.72, 32, 32), haloMat)
    halo.renderOrder = 3
    scene.add(halo)

    // Pulse rings (2 staggered)
    const makeRing = () => {
      const mat = new THREE.ShaderMaterial({
        uniforms: { uOpa: { value: 0 } },
        vertexShader: `void main(){gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}`,
        fragmentShader: `uniform float uOpa;void main(){gl_FragColor=vec4(.55,.18,1.,uOpa);}`,
        transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending,
      })
      const mesh = new THREE.Mesh(new THREE.RingGeometry(1.22, 1.30, 72), mat)
      mesh.renderOrder = 4
      return { mesh, mat }
    }
    const rings = [makeRing(), makeRing()]
    rings.forEach(r => scene.add(r.mesh))
    const ringOffsets = [0, 2.8]

    // Background stars
    const starCount = 280
    const starPos = new Float32Array(starCount * 3)
    const starSz = new Float32Array(starCount)
    let si = 0
    while (si < starCount) {
      const x = (Math.random() - 0.5) * 12
      const y = (Math.random() - 0.5) * 12
      const z = (Math.random() - 0.5) * 4
      const r = Math.sqrt(x * x + y * y + z * z)
      if (r < 2.4 || r > 7) continue
      starPos[si * 3] = x; starPos[si * 3 + 1] = y; starPos[si * 3 + 2] = z
      starSz[si] = Math.random() * 2.2 + 0.6
      si++
    }
    const starGeo = new THREE.BufferGeometry()
    starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3))
    starGeo.setAttribute('size', new THREE.BufferAttribute(starSz, 1))
    const starMat = new THREE.ShaderMaterial({
      uniforms: {},
      vertexShader: `attribute float size;void main(){vec4 mv=modelViewMatrix*vec4(position,1.);gl_PointSize=size*(280./-mv.z);gl_Position=projectionMatrix*mv;}`,
      fragmentShader: `void main(){vec2 uv=gl_PointCoord*2.-1.;float d=length(uv);if(d>1.)discard;float a=pow(1.-d,2.)*.55;gl_FragColor=vec4(.80,.70,1.,a);}`,
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    })
    const stars = new THREE.Points(starGeo, starMat)
    stars.renderOrder = 0
    scene.add(stars)

    // Mouse tracking
    let rawMX = 0, rawMY = 0
    const mSpring = new Spring2D(0.040, 0.87)
    const onMouseMove = e => { rawMX = (e.clientX / W()) * 2 - 1; rawMY = -((e.clientY / H()) * 2 - 1) }
    const onTouchMove = e => { rawMX = (e.touches[0].clientX / W()) * 2 - 1; rawMY = -((e.touches[0].clientY / H()) * 2 - 1) }
    window.addEventListener('mousemove', onMouseMove, { passive: true })
    window.addEventListener('touchmove', onTouchMove, { passive: true })

    // Notification cards — float phase config per card
    const notifs = Array.from(container.querySelectorAll('.notif'))
    const FP = [
      { ph: 0,               sp: 0.32, ax: 7,  ay: 11, par: 0.028 },
      { ph: Math.PI * 0.73,  sp: 0.26, ax: 9,  ay: 8,  par: 0.022 },
      { ph: Math.PI * 1.4,   sp: 0.34, ax: 6,  ay: 13, par: 0.031 },
      { ph: Math.PI * 0.42,  sp: 0.28, ax: 11, ay: 7,  par: 0.025 },
    ]
    const nPos = notifs.map(() => new Spring2D(0.050, 0.83))
    const nSc  = notifs.map(() => { const s = new Spring1D(0.10, 0.77); s.c = 0.82; return s })
    const nOp  = notifs.map(() => new Spring1D(0.08, 0.82))
    const nHov = notifs.map(() => false)
    const nOpn = notifs.map(() => false)

    const timers = []
    notifs.forEach((el, i) => {
      nSc[i].to(0); nOp[i].to(0)
      const t = setTimeout(() => {
        nSc[i].to(1); nOp[i].to(1)
        const fill = el.querySelector('.mfill')
        if (fill) fill.style.width = fill.dataset.target + '%'
      }, parseInt(el.dataset.delay || '600'))
      timers.push(t)
      const onEnter = () => { nHov[i] = true; nSc[i].to(1.07) }
      const onLeave = () => { nHov[i] = false; if (!nOpn[i]) nSc[i].to(1) }
      const onClick = () => {
        nOpn[i] = !nOpn[i]
        el.classList.toggle('open', nOpn[i])
        nSc[i].to(nOpn[i] ? 1.09 : (nHov[i] ? 1.07 : 1))
      }
      el.addEventListener('mouseenter', onEnter)
      el.addEventListener('mouseleave', onLeave)
      el.addEventListener('click', onClick)
    })

    // Animation loop
    const pRM = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    let rafId, prevTS = 0, elapsed = 0, baseRY = 0
    const allUTime = [glowMesh.material, innerMesh.material, blobMat, haloMat]

    const tick = ts => {
      rafId = requestAnimationFrame(tick)
      const dt = Math.min((ts - prevTS) / 1000, 0.05)
      prevTS = ts
      if (!pRM) elapsed += dt
      const t = elapsed

      allUTime.forEach(m => { m.uniforms.uTime.value = t })
      stars.rotation.y = t * 0.012

      if (!pRM) {
        baseRY += 0.00075
        mSpring.to(rawMX, rawMY)
        mSpring.step()

        const ry = baseRY + mSpring.x * 0.22
        const rx = mSpring.y * -0.17
        const fy = Math.sin(t * 0.50) * 0.062

        blob.rotation.y = innerMesh.rotation.y = ry
        blob.rotation.x = innerMesh.rotation.x = rx
        blob.position.y = innerMesh.position.y = fy
        halo.rotation.y = baseRY * 0.55
        halo.position.y = fy
        glowMesh.position.y = fy

        rings.forEach((ring, i) => {
          const progress = ((t - ringOffsets[i]) % 6.0) / 3.0
          const p = Math.max(0, Math.min(1, progress))
          ring.mesh.scale.setScalar(1.0 + p * 1.85)
          ring.mesh.position.y = fy
          ring.mat.uniforms.uOpa.value = (1 - p) * (1 - p) * 0.5
        })
      }

      notifs.forEach((el, i) => {
        if (!pRM) {
          const fp = FP[i]
          const ft = t * fp.sp + fp.ph
          const fx = Math.sin(ft) * fp.ax
          const fy2 = Math.cos(ft * 0.72) * fp.ay
          const px = mSpring.x * fp.par * W() * 0.038
          const py = -mSpring.y * fp.par * H() * 0.038
          if (!nHov[i] && !nOpn[i]) nPos[i].to(fx + px, fy2 + py)
          nPos[i].step()
        }
        const sc = nSc[i].step(), op = nOp[i].step()
        const tx = pRM ? 0 : nPos[i].x, ty = pRM ? 0 : nPos[i].y
        el.style.transform = `translate(${tx.toFixed(2)}px,${ty.toFixed(2)}px) scale(${sc.toFixed(4)})`
        el.style.opacity = op.toFixed(4)
      })

      renderer.render(scene, camera)
    }
    rafId = requestAnimationFrame(tick)

    const onResize = () => {
      camera.aspect = W() / H()
      camera.updateProjectionMatrix()
      renderer.setSize(W(), H())
    }
    window.addEventListener('resize', onResize, { passive: true })

    return () => {
      cancelAnimationFrame(rafId)
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('touchmove', onTouchMove)
      window.removeEventListener('resize', onResize)
      timers.forEach(clearTimeout)
      sharedGeo.dispose()
      starGeo.dispose()
      halo.geometry.dispose()
      rings.forEach(r => { r.mesh.geometry.dispose(); r.mat.dispose() })
      glowMesh.material.dispose()
      innerMesh.material.dispose()
      blobMat.dispose()
      haloMat.dispose()
      starMat.dispose()
      renderer.dispose()
    }
  }, [])

  return (
    <div ref={containerRef} className={`aurora-ring ${className}`} style={{ height }} aria-hidden="true">
      <canvas ref={canvasRef} className="aurora-three-canvas" />

      <div className="notif-layer">
        {/* Job Match */}
        <div className="notif n1" data-delay="600">
          <div className="notif-row">
            <div className="notif-icon ni-m">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="8" r="6.2" stroke="#a78bfa" strokeWidth="1.4"/>
                <path d="M5 8l2.2 2.2L11 6" stroke="#a78bfa" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
            <div className="notif-title">Job Match Found</div>
            <div className="ndot dg"></div>
          </div>
          <div className="notif-desc">Frontend Developer at Stripe</div>
          <div className="mbar">
            <div className="mtrack"><div className="mfill" data-target="94"></div></div>
            <div className="mpct">94%</div>
          </div>
          <div className="nfoot">
            <div className="ntime">2 min ago</div>
            <div className="ntag">Match</div>
          </div>
          <div className="nextra">React · TypeScript · Remote · $140–170k</div>
        </div>

        {/* Application Update */}
        <div className="notif n2" data-delay="1300">
          <div className="notif-row">
            <div className="notif-icon ni-u">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M3 8h10M9 4l4 4-4 4" stroke="#38bdf8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
            </div>
            <div className="notif-title">Application Update</div>
            <div className="ndot db"></div>
          </div>
          <div className="notif-desc">Moved to technical interview stage</div>
          <div className="nfoot">
            <div className="ntime">1 hr ago</div>
            <div className="ntag">Interview</div>
          </div>
          <div className="nextra">Vercel — Senior Engineer · Stage 3 of 4</div>
        </div>

        {/* AI Recommendation */}
        <div className="notif n3" data-delay="920">
          <div className="notif-row">
            <div className="notif-icon ni-a">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <path d="M8 2l1.4 3.8H14L10.3 8l1.4 3.8L8 9.5l-3.7 2.3 1.4-3.8L2 5.8h4.6L8 2z" fill="#c4b5fd"/>
              </svg>
            </div>
            <div className="notif-title">AI Recommendation</div>
            <div className="ndot dv"></div>
          </div>
          <div className="notif-desc">3 new roles match your profile</div>
          <div className="nfoot">
            <div className="ntime">Just now</div>
            <div className="ntag">New</div>
          </div>
          <div className="nextra">Linear · Figma · Notion — all hiring remote</div>
        </div>

        {/* Interview Reminder */}
        <div className="notif n4" data-delay="1750">
          <div className="notif-row">
            <div className="notif-icon ni-c">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <rect x="2.5" y="3.5" width="11" height="10" rx="1.5" stroke="#fbbf24" strokeWidth="1.35"/>
                <path d="M5.5 2v3M10.5 2v3M2.5 7.5h11" stroke="#fbbf24" strokeWidth="1.35" strokeLinecap="round"/>
              </svg>
            </div>
            <div className="notif-title">Interview Reminder</div>
            <div className="ndot da"></div>
          </div>
          <div className="notif-desc">Technical round tomorrow at 10:00 AM</div>
          <div className="nfoot">
            <div className="ntime">Tomorrow</div>
            <div className="ntag">Scheduled</div>
          </div>
          <div className="nextra">Loom · System design + coding · 90 min</div>
        </div>
      </div>

      <div className="aurora-vignette" />
    </div>
  )
}
