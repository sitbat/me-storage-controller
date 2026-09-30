package dev.mestorage.controller.client;

record DashboardPalette(int background, int panel, int inset, int border, int text, int muted,
                        int accent, int selected, int hover, int slot, int danger, int warning) {
    static final DashboardPalette DARK = new DashboardPalette(0xff101823, 0xff182331, 0xff121c28,
            0xff2b3a4c, 0xffe8f0fa, 0xffa2b2c6, 0xff66dbc2, 0xff24483f, 0xff253447,
            0xff293849, 0xfff18a91, 0xffe8bc6b);
    static final DashboardPalette LIGHT = new DashboardPalette(0xffedf2f7, 0xffffffff, 0xfff5f8fc,
            0xffcbd5e1, 0xff1b2a3d, 0xff586c84, 0xff087c69, 0xffd9eee8, 0xffe7edf6,
            0xffe0e8f1, 0xffbd3948, 0xff9a660c);

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
