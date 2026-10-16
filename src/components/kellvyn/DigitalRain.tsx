import { useEffect, useRef } from "react";
import type { Effect } from "./store";

type P = { x: number; y: number; vx: number; vy: number; size: number; alpha: number; rot: number; vr: number; phase: number };

export function calculateParticleCount(effect: Effect, width: number, height: number): number {
  if (effect === "nenhum") return 0;
  if (effect === "raios") return 60;
  const area = Math.max(0, width) * Math.max(0, height);
  const density = effect === "folhas" ? 22000 : effect === "neve" ? 5000 : 7000;
  const cap = effect === "chuva" ? 110 : 160;
  const minimum = effect === "chuva" ? 20 : 0;
  return Math.min(cap, Math.max(minimum, Math.round(area / density)));
}

function toRgb(value: string): [number, number, number] {
  const hex = /^#[0-9a-f]{6}$/i.test(value) ? value.slice(1) : "38E6BC";
  return [parseInt(hex.slice(0, 2), 16), parseInt(hex.slice(2, 4), 16), parseInt(hex.slice(4, 6), 16)];
}

/** Lightweight screen rain, drawn behind the chat and tuned for Android phones. */
export function DigitalRain({ effect, color }: { effect: Effect; color: string }) {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    if (effect === "nenhum") return;
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d", { alpha: true });
    if (!canvas || !ctx) return;

    const rgb = toRgb(color);
    const sprite = document.createElement("canvas");
    sprite.width = 4;
    sprite.height = 64;
    const sctx = sprite.getContext("2d");
    if (!sctx) return;
    const gradient = sctx.createLinearGradient(0, 0, 0, 64);
    gradient.addColorStop(0, "rgba(" + rgb.join(",") + ",0)");
    gradient.addColorStop(1, "rgba(" + rgb.join(",") + ",0.95)");
    sctx.fillStyle = gradient;
    sctx.fillRect(1, 0, 2, 64);

    const leafColors = ["#C9682B", "#D9A441", "#8E3B1F", "#A8B04A"];
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let width = 0;
    let height = 0;
    let raf = 0;
    let lastFrame = 0;
    let flash = 0;
    let nextBolt = 2 + Math.random() * 4;
    let bolt: { x: number; y: number }[] = [];
    let particles: P[] = [];

    const spawn = (randomY: boolean): P => {
      const d = Math.random();
      const base = { x: Math.random() * width, y: randomY ? Math.random() * height : -20 - Math.random() * 60, rot: Math.random() * 6.28, phase: Math.random() * 6.28 };
      if (effect === "neve") return { ...base, vx: 0, vy: 25 + d * 55, size: 1 + d * 2.6, alpha: 0.4 + d * 0.5, vr: 0 };
      if (effect === "folhas") return { ...base, vx: 0, vy: 30 + d * 45, size: 5 + d * 6, alpha: 0.55 + d * 0.4, vr: (Math.random() - 0.5) * 2.5 };
      return { ...base, vx: (Math.random() - 0.5) * 5, vy: 105 + d * 210, size: 18 + d * 36, alpha: 0.3 + d * 0.36, vr: d > 0.8 ? 1.6 : 1 };
    };

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      width = window.innerWidth;
      height = window.innerHeight;
      canvas.width = Math.round(width * dpr);
      canvas.height = Math.round(height * dpr);
      canvas.style.width = width + "px";
      canvas.style.height = height + "px";
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      particles = Array.from({ length: calculateParticleCount(effect, width, height) }, () => spawn(true));
    };

    const makeBolt = () => {
      let current = { x: width * (0.15 + Math.random() * 0.7), y: 0 };
      const points = [current];
      const end = height * (0.45 + Math.random() * 0.3);
      while (current.y < end) {
        current = { x: current.x + (Math.random() - 0.5) * 50, y: current.y + 15 + Math.random() * 30 };
        points.push(current);
      }
      return points;
    };

    const paint = (now: number, dt: number) => {
      const time = now / 1000;
      ctx.clearRect(0, 0, width, height);
      for (const p of particles) {
        if (effect === "neve") p.x += Math.sin(time * 0.8 + p.phase) * 18 * dt;
        if (effect === "folhas") { p.x += Math.sin(time * 1.2 + p.phase) * 40 * dt; p.rot += p.vr * dt; }
        if (effect === "chuva") p.x += p.vx * dt;
        p.y += p.vy * dt;
        if (p.y - p.size > height) Object.assign(p, spawn(false));
        ctx.globalAlpha = p.alpha;
        if (effect === "neve") {
          ctx.fillStyle = "#FFFFFF";
          ctx.beginPath(); ctx.arc(p.x, p.y, p.size, 0, 6.283); ctx.fill();
        } else if (effect === "folhas") {
          ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
          ctx.fillStyle = leafColors[Math.floor(p.phase) % leafColors.length] ?? "#C9682B";
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
          ctx.fillStyle = "#DDE8FF"; ctx.fillRect(0, 0, width, height);
          ctx.globalAlpha = flash * 0.85;
          ctx.strokeStyle = "#EAF1FF"; ctx.lineWidth = 1.6;
          ctx.shadowColor = "#9DB8FF"; ctx.shadowBlur = 14;
          ctx.beginPath(); bolt.forEach((point, i) => (i ? ctx.lineTo(point.x, point.y) : ctx.moveTo(point.x, point.y))); ctx.stroke();
          ctx.shadowBlur = 0;
          flash -= dt * 2.2;
        }
      }
      ctx.globalAlpha = 1;
    };

    const frame = (now: number) => {
      if (document.hidden) { raf = 0; return; }
      const frameInterval = window.innerWidth < 700 ? 1000 / 30 : 1000 / 45;
      if (lastFrame && now - lastFrame < frameInterval) { raf = requestAnimationFrame(frame); return; }
      const dt = lastFrame ? Math.min((now - lastFrame) / 1000, 0.08) : 1 / 30;
      lastFrame = now;
      paint(now, dt);
      raf = reducedMotion ? 0 : requestAnimationFrame(frame);
    };

    const onResize = () => {
      resize();
      if (reducedMotion) { cancelAnimationFrame(raf); raf = requestAnimationFrame(frame); }
    };
    const onVisibility = () => {
      cancelAnimationFrame(raf);
      raf = 0;
      if (!document.hidden) { lastFrame = 0; raf = requestAnimationFrame(frame); }
    };

    resize();
    window.addEventListener("resize", onResize);
    document.addEventListener("visibilitychange", onVisibility);
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      window.removeEventListener("resize", onResize);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [effect, color]);

  if (effect === "nenhum") return null;
  return <canvas ref={ref} aria-hidden className="pointer-events-none fixed inset-0 z-0" />;
}
