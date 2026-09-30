package dev.mestorage.controller.client;

record DashboardPalette(int background, int panel, int inset, int border, int text, int muted,
                        int accent, int selected, int hover, int slot, int danger, int warning) {
    static final DashboardPalette DARK = new DashboardPalette(0xff454650, 0xff454650, 0xff2d303c,
            0xff171821, 0xffe0e1e8, 0xffadb0c0, 0xffa0b7d4, 0xff586b83, 0xff55596b,
            0xff343745, 0xffe99b96, 0xffd3bf92);
    static final DashboardPalette LIGHT = new DashboardPalette(0xffc9cad2, 0xffc9cad2, 0xffa9aec0,
            0xff484659, 0xff413f54, 0xff656b81, 0xff526c94, 0xfface9ff, 0xffb8c3d6,
            0xffa9aec0, 0xffa84742, 0xff8b671e);

    static DashboardPalette blend(float dark) {
        return new DashboardPalette(mix(LIGHT.background, DARK.background, dark), mix(LIGHT.panel, DARK.panel, dark),
                mix(LIGHT.inset, DARK.inset, dark), mix(LIGHT.border, DARK.border, dark),
                mix(LIGHT.text, DARK.text, dark), mix(LIGHT.muted, DARK.muted, dark),
                mix(LIGHT.accent, DARK.accent, dark), mix(LIGHT.selected, DARK.selected, dark),
                mix(LIGHT.hover, DARK.hover, dark), mix(LIGHT.slot, DARK.slot, dark),
                mix(LIGHT.danger, DARK.danger, dark), mix(LIGHT.warning, DARK.warning, dark));
    }

    static int mix(int from, int to, float progress) {
        float t = Math.max(0, Math.min(1, progress));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }
}
