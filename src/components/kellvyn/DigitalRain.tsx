import { useEffect, useRef } from "react";

/**
 * Smooth digital rain behind the chat.
 * Uses one pre-rendered streak sprite (no per-frame gradients), so it stays fluid on phones.
 */
export function DigitalRain({ color, enabled }: { color: string; enabled: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!enabled) return;
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d", { alpha: true });
    if (!canvas || !ctx) return;

    // Pre-render a single streak (fading tail + bright head) once.
    const SPRITE_H = 64;
    const sprite = document.createElement("canvas");
    sprite.width = 4; sprite.height = SPRITE_H;
    const sctx = sprite.getContext("2d")!;
    const g = sctx.createLinearGradient(0, 0, 0, SPRITE_H);
    g.addColorStop(0, "rgba(0,0,0,0)");
    g.addColorStop(0.85, color);
    g.addColorStop(1, "#ffffff");
    sctx.fillStyle = g;
    sctx.fillRect(1, 0, 2, SPRITE_H);

    type Drop = { x: number; y: number; len: number; speed: number; alpha: number; w: number };
    let w = 0, h = 0, raf = 0, last = 0;
    let drops: Drop[] = [];

    const spawn = (randomY: boolean): Drop => {
      const depth = Math.random(); // 0 = far, 1 = near (parallax)
      return {
        x: Math.random() * w,
        y: randomY ? Math.random() * h : -Math.random() * h * 0.3,
        len: 18 + depth * 50,
        speed: 120 + depth * 380,
        alpha: 0.15 + depth * 0.45,
        w: depth > 0.8 ? 1.6 : 1,
      };
    };

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = window.innerWidth; h = window.innerHeight;
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
      canvas.style.width = `${w}px`; canvas.style.height = `${h}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const count = Math.min(140, Math.round((w * h) / 6000));
      drops = Array.from({ length: count }, () => spawn(true));
    };
    resize();

    const frame = (now: number) => {
      const dt = last ? Math.min((now - last) / 1000, 1 / 30) : 1 / 60;
      last = now;
      ctx.clearRect(0, 0, w, h);
      for (const d of drops) {
        d.y += d.speed * dt;
        if (d.y - d.len > h) Object.assign(d, spawn(false));
        ctx.globalAlpha = d.alpha;
        ctx.drawImage(sprite, d.x, d.y - d.len, d.w * 2, d.len);
      }
      ctx.globalAlpha = 1;
      raf = requestAnimationFrame(frame);
    };

    const onVis = () => {
      cancelAnimationFrame(raf);
      if (!document.hidden) { last = 0; raf = requestAnimationFrame(frame); }
    };
    window.addEventListener("resize", resize);
    document.addEventListener("visibilitychange", onVis);
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, [color, enabled]);

  if (!enabled) return null;
  return <canvas ref={ref} aria-hidden className="pointer-events-none fixed inset-0 z-0" />;
}
