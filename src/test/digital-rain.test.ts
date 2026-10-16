import { describe, expect, it } from "vitest";
import { calculateParticleCount } from "@/components/kellvyn/DigitalRain";

describe("DigitalRain particle budget", () => {
  it("keeps rain visible on a Poco-sized viewport without exceeding the mobile budget", () => {
    const count = calculateParticleCount("chuva", 393, 873);
    expect(count).toBeGreaterThanOrEqual(20);
    expect(count).toBeLessThanOrEqual(110);
  });

  it("caps particles on large screens", () => {
    expect(calculateParticleCount("chuva", 1440, 900)).toBe(110);
  });

  it("creates no particles when the effect is disabled", () => {
    expect(calculateParticleCount("nenhum", 393, 873)).toBe(0);
  });
});
