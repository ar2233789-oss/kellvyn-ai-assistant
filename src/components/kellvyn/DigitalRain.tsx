import { useEffect, useRef } from "react";
import type { Effect } from "./store";

type P = { x: number; y: number; vx: number; vy: number; size: number; alpha: number; rot: number; vr: number; phase: number };

/** Falling-screen effects (rain, snow, leaves) plus soft lightning. Drawn behind the chat. */
export function DigitalRain({ effect }: { effect: Effect }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (effect === "nenhum") return;
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;

    // Rain sprite: pre-rendered once so each frame is just drawImage calls.
    const sprite = document.createElement("canvas");
    sprite.width = 4; sprite.height = 64;
    const sctx = sprite.getContext("2d")!;
    const g = sctx.createLinearGradient(0, 0, 0, 64);
    g.addColorStop(0, "rgba(207,227,242,0)");
    g.addColorStop(1, "rgba(220,235,248,1)");
    sctx.fillStyle = g; sctx.fillRect(1, 0, 2, 64);

    const LEAF = ["#C9682B", "#D9A441", "#8E3B1F", "#A8B04A"];
    let w = 0, h = 0, raf = 0, last = 0, flash = 0, nextBolt = 2 + Math.random() * 4;
    let bolt: { x: number; y: number }[] = [];
    let ps: P[] = [];

    const spawn = (randomY: boolean): P => {
      const d = Math.random();
      const base = { x: Math.random() * w, y: randomY ? Math.random() * h : -20 - Math.random() * 60, rot: Math.random() * 6.28, phase: Math.random() * 6.28 };
      if (effect === "neve") return { ...base, vx: 0, vy: 25 + d * 55, size: 1 + d * 2.6, alpha: 0.4 + d * 0.5, vr: 0 };
      if (effect === "folhas") return { ...base, vx: 0, vy: 30 + d * 45, size: 5 + d * 6, alpha: 0.55 + d * 0.4, vr: (Math.random() - 0.5) * 2.5 };
      return { ...base, vx: 0, vy: 140 + d * 380, size: 18 + d * 50, alpha: 0.15 + d * 0.4, vr: d > 0.8 ? 1.6 : 1 };
    };

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      w = window.innerWidth; h = window.innerHeight;
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
      canvas.style.width = `${w}px`; canvas.style.height = `${h}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const density = effect === "folhas" ? 22000 : effect === "neve" ? 5000 : 6000;
      const n = effect === "raios" ? 60 : Math.min(160, Math.round((w * h) / density));
      ps = Array.from({ length: n }, () => spawn(true));
    };
    resize();

    const makeBolt = () => {
      const pts = [{ x: w * (0.15 + Math.random() * 0.7), y: 0 }];
      while (pts[pts.length - 1].y < h * (0.45 + Math.random() * 0.3)) {
        const p = pts[pts.length - 1];
        pts.push({ x: p.x + (Math.random() - 0.5) * 50, y: p.y + 15 + Math.random() * 30 });
      }
      return pts;
    };

    const frame = (now: number) => {
      const dt = last ? Math.min((now - last) / 1000, 1 / 30) : 1 / 60;
      last = now;
      const t = now / 1000;
      ctx.clearRect(0, 0, w, h);

      for (const p of ps) {
        if (effect === "neve") p.x += Math.sin(t * 0.8 + p.phase) * 18 * dt;
        if (effect === "folhas") { p.x += Math.sin(t * 1.2 + p.phase) * 40 * dt; p.rot += p.vr * dt; }
        p.y += p.vy * dt;
        if (p.y - 60 > h) Object.assign(p, spawn(false));
        ctx.globalAlpha = p.alpha;
        if (effect === "neve") {
          ctx.fillStyle = "#FFFFFF";
          ctx.beginPath(); ctx.arc(p.x, p.y, p.size, 0, 6.283); ctx.fill();
        } else if (effect === "folhas") {
          ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
          ctx.fillStyle = LEAF[Math.floor(p.phase) % LEAF.length];
          ctx.beginPath(); ctx.ellipse(0, 0, p.size, p.size * 0.45, 0, 0, 6.283); ctx.fill();
          ctx.restore();
        } else {
          ctx.drawImage(sprite, p.x, p.y - p.size, p.vr * 2, p.size);
        }
      }

      if (effect === "raios") {
        nextBolt -= dt;
        if (nextBolt <= 0) { bolt = makeBolt(); flash = 1; nextBolt = 3 + Math.random() * 6; }
        if (flash > 0) {
          ctx.globalAlpha = flash * 0.12;
          ctx.fillStyle = "#DDE8FF"; ctx.fillRect(0, 0, w, h);
          ctx.globalAlpha = flash * 0.85;
          ctx.strokeStyle = "#EAF1FF"; ctx.lineWidth = 1.6;
          ctx.shadowColor = "#9DB8FF"; ctx.shadowBlur = 14;
          ctx.beginPath(); bolt.forEach((b, i) => (i ? ctx.lineTo(b.x, b.y) : ctx.moveTo(b.x, b.y))); ctx.stroke();
          ctx.shadowBlur = 0;
          flash -= dt * 2.2;
        }
      }
      ctx.globalAlpha = 1;
      raf = requestAnimationFrame(frame);
    };

    const onVis = () => { cancelAnimationFrame(raf); if (!document.hidden) { last = 0; raf = requestAnimationFrame(frame); } };
    window.addEventListener("resize", resize);
    document.addEventListener("visibilitychange", onVis);
    raf = requestAnimationFrame(frame);
    return () => { cancelAnimationFrame(raf); window.removeEventListener("resize", resize); document.removeEventListener("visibilitychange", onVis); };
  }, [effect]);

  if (effect === "nenhum") return null;
  return <canvas ref={ref} aria-hidden className="pointer-events-none fixed inset-0 z-0" />;
}
