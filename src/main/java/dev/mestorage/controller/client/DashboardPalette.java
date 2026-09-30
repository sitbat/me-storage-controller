package dev.mestorage.controller.client;

record DashboardPalette(int background, int panel, int inset, int border, int text, int muted,
                        int accent, int selected, int hover, int slot, int danger, int warning) {
    static final DashboardPalette DARK = new DashboardPalette(0xff14181f, 0xff1b212b, 0xff161c25,
            0xff323d4c, 0xffe7edf5, 0xffa1b1c4, 0xff78c9f1, 0xff263f54, 0xff273342,
            0xff27303d, 0xffff9c9c, 0xffebc278);
    static final DashboardPalette LIGHT = new DashboardPalette(0xfff0f3f7, 0xfffcfdff, 0xfff3f6fa,
            0xffc9d3df, 0xff202d40, 0xff586b82, 0xff17628b, 0xffdcedf9, 0xffe7eef6,
            0xffe2eaf3, 0xffb03c4a, 0xff916015);

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
