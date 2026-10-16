import { useEffect, useRef } from "react";

/** Subtle falling digital rain drawn behind the chat. Fixed DPR handling + pauses when hidden. */
export function DigitalRain({ color, enabled }: { color: string; enabled: boolean }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (!enabled) return;
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    let w = 0, h = 0, raf = 0, last = performance.now();
    type Drop = { x: number; y: number; len: number; speed: number; alpha: number; width: number };
    let drops: Drop[] = [];

    const spawn = (randomY: boolean): Drop => ({
      x: Math.random() * w,
      y: randomY ? Math.random() * h : -Math.random() * 120,
      len: 14 + Math.random() * 46,
      speed: 140 + Math.random() * 260,
      alpha: 0.12 + Math.random() * 0.35,
      width: Math.random() < 0.15 ? 1.6 : 1,
    });

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = canvas.clientWidth; h = canvas.clientHeight;
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const count = Math.round((w * h) / 9000);
      drops = Array.from({ length: count }, () => spawn(true));
    };
    resize();
    window.addEventListener("resize", resize);

    const frame = (now: number) => {
      const dt = Math.min((now - last) / 1000, 0.05);
      last = now;
      ctx.clearRect(0, 0, w, h);
      for (const d of drops) {
        d.y += d.speed * dt;
        if (d.y - d.len > h) Object.assign(d, spawn(false));
        const g = ctx.createLinearGradient(d.x, d.y - d.len, d.x, d.y);
        g.addColorStop(0, "transparent");
        g.addColorStop(1, color);
        ctx.globalAlpha = d.alpha;
        ctx.strokeStyle = g;
        ctx.lineWidth = d.width;
        ctx.beginPath();
        ctx.moveTo(d.x, d.y - d.len);
        ctx.lineTo(d.x, d.y);
        ctx.stroke();
        ctx.fillStyle = color;
        ctx.globalAlpha = d.alpha * 1.6;
        ctx.fillRect(d.x - 0.75, d.y - 1, 1.5, 2);
      }
      ctx.globalAlpha = 1;
      if (!reduce) raf = requestAnimationFrame(frame);
    };
    const onVis = () => {
      cancelAnimationFrame(raf);
      if (!document.hidden) { last = performance.now(); raf = requestAnimationFrame(frame); }
    };
    document.addEventListener("visibilitychange", onVis);
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", resize);
      document.removeEventListener("visibilitychange", onVis);
    };
  }, [color, enabled]);

  if (!enabled) return null;
  return <canvas ref={ref} aria-hidden className="pointer-events-none fixed inset-0 z-0 h-full w-full" />;
}
