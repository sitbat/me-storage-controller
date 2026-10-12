package dev.mestorage.controller.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A vanilla text editor whose glyphs are rendered without a drop shadow. */
final class ShadowlessEditBox extends EditBox {
    ShadowlessEditBox(Font font,int x,int y,int width,int height,Component label){
        super(font,x,y,width,height,label);
    }

    @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        // All input, selection, caret and horizontal-scroll calculations remain in EditBox.
        super.renderWidget(new ShadowlessTextGraphics(graphics),mouseX,mouseY,partialTick);
    }

    private static final class ShadowlessTextGraphics extends GuiGraphics {
        private final GuiGraphics delegate;
        ShadowlessTextGraphics(GuiGraphics delegate){super(Minecraft.getInstance(),delegate.bufferSource());this.delegate=delegate;}
        // Vanilla's shadowed draw returns the final X plus one. Keep that advance
        // because EditBox compensates for it when positioning its caret and suffix.
        @Override public int drawString(Font font,FormattedCharSequence text,int x,int y,int color){return delegate.drawString(font,text,x,y,color,false)+1;}
        @Override public int drawString(Font font,String text,int x,int y,int color){return delegate.drawString(font,text,x,y,color,false)+1;}
        @Override public int drawString(Font font,Component text,int x,int y,int color){return delegate.drawString(font,text,x,y,color,false)+1;}
        // Keep the caller's pose, clipping and render layer for the caret and selection.
        @Override public void fill(int x1,int y1,int x2,int y2,int color){delegate.fill(x1,y1,x2,y2,color);}
        @Override public void fill(RenderType type,int x1,int y1,int x2,int y2,int color){delegate.fill(type,x1,y1,x2,y2,color);}
    }
}
