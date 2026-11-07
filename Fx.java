package dev.charter;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Gemeinsame Partikel-Animationen (nur Vanilla-Partikel). */
public final class Fx {

    public static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(170, 0, 25), 1.4f);
    public static final Particle.DustOptions BONE = new Particle.DustOptions(Color.fromRGB(235, 230, 210), 1.2f);
    public static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(70, 220, 230), 1.2f);
    public static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 60), 1.3f);

    private Fx() {}

    public static void ring(Location c, double r, int points, Particle p, Object data) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            c.getWorld().spawnParticle(p, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0, data);
        }
    }

    /** Aufsteigende Doppelhelix ueber {@code ticks} Ticks. */
    public static void spiralUp(Plugin pl, Location base, double radius, double height, int ticks,
                                Particle p, Object data) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks) {
                    cancel();
                    return;
                }
                double y = height * t / ticks;
                for (int s = 0; s < 2; s++) {
                    double a = t * 0.55 + s * Math.PI;
                    base.getWorld().spawnParticle(p, base.getX() + Math.cos(a) * radius, base.getY() + y,
                            base.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0, data);
                }
                t++;
            }
        }.runTaskTimer(pl, 0, 1);
    }

    public static void beam(Location from, Location to, Particle p, Object data) {
        double dist = from.distance(to);
        int steps = Math.max(1, (int) (dist / 0.5));
        for (int i = 0; i <= steps; i++) {
            double f = (double) i / steps;
            from.getWorld().spawnParticle(p,
                    from.getX() + (to.getX() - from.getX()) * f,
                    from.getY() + (to.getY() - from.getY()) * f,
                    from.getZ() + (to.getZ() - from.getZ()) * f,
                    1, 0, 0, 0, 0, data);
        }
    }
}
