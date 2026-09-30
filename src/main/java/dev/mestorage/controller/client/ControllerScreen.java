package dev.mestorage.controller.client;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Snapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** Responsive dashboard, retaining vanilla server-authoritative inventory interaction. */
public final class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final int RIGHT=210;
    private EditBox deviceSearch,contentSearch;
    private boolean sortByAmount=true;
    private long searchDue,previousFrame;
    private int rightWidth,searchY,devicesY,deviceRowH,cellY,inventoryY,hotbarY;
    private int metricY,contentSearchY,contentsY,contentRowH,footerY;
    private float spacious,frameDelta=.016F,darkMix=ClientAppearance.isDark()?1:0;
    private DashboardPalette palette=DashboardPalette.blend(darkMix);
    private Button back,locate,theme,devicePrevious,deviceNext,contentPrevious,contentNext,cellsPrevious,cellsNext;
    private final Map<String,Snapshot.DeviceInfo> knownDevices=new HashMap<>();
    private final Map<Integer,Float> cellProgress=new HashMap<>();
    private final float[] deviceHover=new float[3],contentHover=new float[6];
    private float mainProgress;
    private String progressIdentity="";

    public ControllerScreen(ControllerMenu menu,Inventory inventory,Component title) { super(menu,inventory,title); }
    private static Component tr(String key,Object... args) { return Component.translatable("gui.me_storage_controller."+key,args); }
    private int interpolate(int compact,int large) { return Math.round(compact+(large-compact)*spacious); }

    @Override protected void init() {
        imageWidth=Math.min(width-12,600); imageHeight=Math.min(height-12,360);
        spacious=Math.max(0,Math.min(1,(imageHeight-228)/132F)); rightWidth=imageWidth-RIGHT-10;
        searchY=interpolate(30,50); devicesY=interpolate(49,73); deviceRowH=interpolate(16,26);
        cellY=interpolate(113,190); inventoryY=imageHeight-81; hotbarY=imageHeight-23;
        metricY=interpolate(32,76); contentSearchY=interpolate(72,137); contentsY=interpolate(94,164);
        contentRowH=Math.max(18,(imageHeight-24-contentsY)/6); footerY=imageHeight-20;
        String oldDevices=deviceSearch==null?"":deviceSearch.getValue(),oldContents=contentSearch==null?"":contentSearch.getValue();
        super.init(); menu.layoutSlots(cellY,inventoryY,hotbarY);
        back=button(imageWidth-123,7,22,18,Component.literal("<"),b->{var s=menu.getSnapshot(); request(s.selectedCell()>=0?s.selectedDevice():"",-1,s.devicePage(),0);});
        back.setTooltip(Tooltip.create(tr("back")));
        locate=button(imageWidth-96,7,37,18,tr("locate"),b->{var d=selectedInfo(menu.getSnapshot()); if(d!=null) DeviceHighlight.show(d.dimension(),d.pos());});
        theme=button(imageWidth-54,7,44,18,themeName(),b->{ClientAppearance.setDark(!ClientAppearance.isDark()); b.setMessage(themeName());});
        theme.setTooltip(Tooltip.create(tr("theme_toggle")));
        deviceSearch=search(8,searchY,190,oldDevices,"device_search");
        contentSearch=search(RIGHT,contentSearchY,rightWidth-35,oldContents,"content_search");
        var sort=button(imageWidth-40,contentSearchY,30,18,Component.literal(sortByAmount?"#":"A"),b->{
            sortByAmount=!sortByAmount; b.setMessage(Component.literal(sortByAmount?"#":"A"));
            b.setTooltip(Tooltip.create(tr(sortByAmount?"sort_amount":"sort_name")));
            var s=menu.getSnapshot(); request(s.selectedDevice(),s.selectedCell(),s.devicePage(),0);
        });
        sort.setTooltip(Tooltip.create(tr(sortByAmount?"sort_amount":"sort_name")));
        int deviceFooter=devicesY+3*deviceRowH+1;
        devicePrevious=button(8,deviceFooter,19,12,Component.literal("<"),b->{var s=menu.getSnapshot();request(s.selectedDevice(),s.selectedCell(),s.devicePage()-1,s.contentPage());});
        deviceNext=button(179,deviceFooter,19,12,Component.literal(">"),b->{var s=menu.getSnapshot();request(s.selectedDevice(),s.selectedCell(),s.devicePage()+1,s.contentPage());});
        contentPrevious=button(RIGHT,footerY,21,14,Component.literal("<"),b->{var s=menu.getSnapshot();request(s.selectedDevice(),s.selectedCell(),s.devicePage(),s.contentPage()-1);});
        contentNext=button(imageWidth-31,footerY,21,14,Component.literal(">"),b->{var s=menu.getSnapshot();request(s.selectedDevice(),s.selectedCell(),s.devicePage(),s.contentPage()+1);});
        cellsPrevious=button(179,inventoryY,19,16,Component.literal("<"),b->{var s=menu.getSnapshot();request(s.selectedDevice(),cellOffset(s)-10,s.devicePage(),0);});
        cellsNext=button(179,inventoryY+19,19,16,Component.literal(">"),b->{var s=menu.getSnapshot();request(s.selectedDevice(),cellOffset(s)+10,s.devicePage(),0);});
        cellsPrevious.setTooltip(Tooltip.create(tr("cells_previous"))); cellsNext.setTooltip(Tooltip.create(tr("cells_next")));
        updateButtons();
    }
    private Component themeName() {return tr(ClientAppearance.isDark()?"theme_dark":"theme_light");}
    private EditBox search(int x,int y,int w,String value,String hint) {
        var box=new EditBox(font,leftPos+x+6,topPos+y+5,Math.max(20,w-12),12,tr(hint));
        box.setBordered(false); box.setMaxLength(64); box.setHint(tr(hint)); box.setValue(value);
        box.setResponder(ignored->searchDue=System.currentTimeMillis()+300); return addRenderableWidget(box);
    }
    private Button button(int x,int y,int w,int h,Component text,Button.OnPress action) {return addRenderableWidget(new DashboardButton(leftPos+x,topPos+y,w,h,text,action));}
    private void request(String device,int cell,int dp,int cp) {
        searchDue=0; menu.request(device,cell,Math.max(0,dp),Math.max(0,cp),deviceSearch.getValue(),contentSearch.getValue(),sortByAmount);
    }
    @Override protected void containerTick() {
        super.containerTick(); deviceSearch.tick();contentSearch.tick();
        if(searchDue!=0&&System.currentTimeMillis()>=searchDue) {var s=menu.getSnapshot();request(s.selectedDevice(),s.selectedCell(),0,0);}
        updateButtons();
    }
    private Snapshot.DeviceInfo selectedInfo(Snapshot s) {return s.selectedInfo()!=null?s.selectedInfo():knownDevices.get(s.selectedDevice());}
    private void updateButtons() {
        var s=menu.getSnapshot(); for(var d:s.devices()) knownDevices.put(d.id(),d);
        back.active=!s.selectedDevice().isEmpty(); var selected=selectedInfo(s); locate.active=selected!=null&&hasLocation(selected);
        theme.setMessage(themeName());
        devicePrevious.active=s.devicePage()>0; deviceNext.active=s.devicePage()+1<s.devicePages();
        contentPrevious.active=s.contentPage()>0; contentNext.active=s.contentPage()+1<s.contentPages();
        cellsPrevious.visible=cellsNext.visible=s.cellSlots()>10;
        cellsPrevious.active=cellOffset(s)>0;cellsNext.active=cellOffset(s)+10<s.cellSlots();
    }
    private static int cellOffset(Snapshot s) {return Math.max(0,s.selectedCell())/10*10;}
    private static boolean hasLocation(Snapshot.DeviceInfo d) {return !d.dimension().toString().equals("me_storage_controller:unknown");}

    @Override protected void renderBg(GuiGraphics g,float partialTick,int mouseX,int mouseY) {
        long now=System.nanoTime(); frameDelta=previousFrame==0?.016F:Math.min(.05F,(now-previousFrame)/1_000_000_000F);previousFrame=now;
        darkMix=animate(darkMix,ClientAppearance.isDark()?1:0,15);palette=DashboardPalette.blend(darkMix);
        var s=menu.getSnapshot();String identity=s.selectedDevice()+"/"+s.selectedCell();
        if(!progressIdentity.equals(identity)){progressIdentity=identity;mainProgress=-1;cellProgress.clear();}
        int x=leftPos,y=topPos;
        rounded(g,x+3,y+5,imageWidth,imageHeight,0x48000000); rounded(g,x,y,imageWidth,imageHeight,palette.border());
        rounded(g,x+1,y+1,imageWidth-2,imageHeight-2,palette.background());
        rounded(g,x+4,y+29,198,imageHeight-33,palette.panel());rounded(g,x+RIGHT-4,y+29,rightWidth+8,imageHeight-33,palette.panel());
        for(int i=0;i<3;i++) rounded(g,x+9,y+8+i*5,12-i*2,3,palette.accent());
        clipped(g,s.selectedDevice().isEmpty()?title:s.title(),x+28,y+11,imageWidth-158,palette.text());
        renderDevices(g,s,mouseX,mouseY);renderCells(g,s,mouseX,mouseY);renderDetails(g,s,mouseX,mouseY);
        searchBackground(g,deviceSearch,8,searchY,190,"device_search");
        searchBackground(g,contentSearch,RIGHT,contentSearchY,rightWidth-35,"content_search");
    }
    private void renderDevices(GuiGraphics g,Snapshot s,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        if(spacious>.7F) {small(g,tr("section_devices"),x+9,y+searchY-13,127,palette.muted());
            g.fill(x+188,y+searchY-11,x+193,y+searchY-6,s.online()?palette.accent():palette.danger());}
        for(int i=0;i<3;i++) {
            int rowY=y+devicesY+i*deviceRowH;boolean over=hit(mouseX,mouseY,x+8,rowY,190,deviceRowH-1);
            deviceHover[i]=animate(deviceHover[i],over?1:0,18);if(i>=s.devices().size())continue;
            var d=s.devices().get(i);boolean selected=d.id().equals(s.selectedDevice());
            rounded(g,x+8,rowY,190,deviceRowH-1,selected?palette.selected():DashboardPalette.mix(palette.inset(),palette.hover(),deviceHover[i]));
            if(selected)g.fill(x+8,rowY+3,x+10,rowY+deviceRowH-4,palette.accent());
            int padding=deviceRowH>=22?3:Math.max(0,(deviceRowH-9)/2);
            if(!d.icon().isEmpty())g.renderItem(d.icon(),x+14,rowY+Math.max(0,(deviceRowH-16)/2));
            else g.fill(x+18,rowY+6,x+24,rowY+12,d.active()?palette.accent():palette.warning());
            clipped(g,d.name(),x+35,rowY+padding,149,palette.text());
            g.fill(x+190,rowY+5,x+193,rowY+8,d.active()?palette.accent():palette.warning());
            if(deviceRowH>=22)small(g,location(d),x+35,rowY+padding+10,153,palette.muted());
        }
        centered(g,tr("page",s.devicePage()+1,Math.max(1,s.devicePages())),x+103,y+devicesY+3*deviceRowH+3,palette.muted());
    }
    private void renderCells(GuiGraphics g,Snapshot s,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        if(spacious>.7F)small(g,tr("section_cells"),x+9,y+cellY-14,170,palette.muted());
        for(int i=0;i<10;i++) {
            int absolute=cellOffset(s)+i;var preview=s.cells().stream().filter(c->c.slot()==absolute).findFirst().orElse(null);
            boolean present=!s.selectedDevice().isEmpty()&&absolute<s.cellSlots();int sx=x+8+18*i;
            slot(g,sx,y+cellY,present&&i<s.editableSlots());
            if(preview!=null&&i>=s.editableSlots()&&!preview.icon().isEmpty())g.renderItem(preview.icon(),sx,y+cellY);
            float target=preview==null||preview.totalBytes()<=0?0:ratio(preview.usedBytes(),preview.totalBytes());
            float fill=animate(cellProgress.getOrDefault(absolute,target),target,12);cellProgress.put(absolute,fill);
            g.fill(sx,y+cellY+17,sx+16,y+cellY+19,palette.border());
            if(preview!=null&&preview.totalBytes()>0)g.fill(sx,y+cellY+17,sx+Math.round(16*fill),y+cellY+19,usageColor(target));
            boolean hovered=hit(mouseX,mouseY,sx,y+cellY+20,16,11);
            rounded(g,sx,y+cellY+20,16,11,s.selectedCell()==absolute?palette.selected():hovered&&present?palette.hover():palette.inset());
            centeredSmall(g,Component.literal(Integer.toString(absolute+1)),sx+8,y+cellY+21,present?palette.text():palette.muted(),14);
        }
        if(spacious>.6F) {
            Component hint=s.cellSlots()==0?tr("select_device"):tr("cell_range",cellOffset(s)+1,Math.min(cellOffset(s)+10,s.cellSlots()),s.cellSlots());
            small(g,hint,x+9,y+cellY+38,181,palette.muted());small(g,tr("section_inventory"),x+9,y+inventoryY-14,164,palette.muted());
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+8+col*18,y+inventoryY+row*18,true);
        for(int col=0;col<9;col++)slot(g,x+8+col*18,y+hotbarY,true);
    }
    private void renderDetails(GuiGraphics g,Snapshot s,int mouseX,int mouseY) {
        int x=leftPos+RIGHT,y=topPos;var cap=s.capacity();long used=cap.usedBytes(),total=cap.totalBytes();
        Component metric=tr("bytes",number(used),number(total));
        if(total<0&&cap.totalSlots()>=0){used=cap.occupiedSlots();total=cap.totalSlots();metric=tr("slots",number(used),number(total));}
        else if(total<0&&cap.fluidCapacity()>=0){used=cap.fluidAmount();total=cap.fluidCapacity();metric=tr("fluid",number(used),number(total));}
        float fraction=total>0&&used>=0?ratio(used,total):0;if(mainProgress<0)mainProgress=fraction;mainProgress=animate(mainProgress,fraction,12);
        if(spacious>.8F) {
            var selected=selectedInfo(s);
            Component breadcrumb=s.selectedDevice().isEmpty()?tr("dashboard"):tr("network").copy().append(" › ").append(selected==null?s.title():selected.name());
            if(s.selectedCell()>=0)breadcrumb=breadcrumb.copy().append(" › ").append(tr("cell",s.selectedCell()+1));
            small(g,breadcrumb,x+6,y+41,rightWidth-72,palette.muted());
            status(g,s.online()&&(selected==null||selected.active()),x+rightWidth-62,y+38);small(g,tr("capacity_used"),x+6,y+metricY-13,rightWidth-12,palette.muted());
        }
        String percent=total>0&&used>=0?percent(used,total):"—";int pw=font.width(percent);
        clipped(g,metric,x+5,y+metricY,rightWidth-pw-17,palette.text());g.drawString(font,percent,x+rightWidth-pw-5,y+metricY,usageColor(fraction),false);
        rounded(g,x+5,y+metricY+13,rightWidth-10,6,palette.border());
        if(mainProgress>0)rounded(g,x+5,y+metricY+13,Math.max(2,Math.round((rightWidth-10)*mainProgress)),6,usageColor(fraction));
        Component secondary=cap.usedTypes()>=0?tr("types",number(cap.usedTypes()),number(cap.totalTypes()))
            :cap.totalSlots()>=0&&cap.fluidCapacity()>=0?tr("fluid",number(cap.fluidAmount()),number(cap.fluidCapacity())):tr("contents",number(s.contentCount()));
        small(g,secondary,x+5,y+metricY+24,rightWidth-10,palette.muted());
        if(spacious>.35F) {
            Component note=!s.error().isEmpty()?Component.translatable(s.error()):!s.online()?tr("offline")
                :cap.unknownCells()>0?tr("unknown_cells",cap.unknownCells()):tr("contents",number(s.contentCount()));
            small(g,note,x+5,y+metricY+37,rightWidth-10,!s.error().isEmpty()||!s.online()?palette.warning():palette.muted());
        }
        for(int i=0;i<6;i++) {
            int rowY=y+contentsY+i*contentRowH;boolean over=hit(mouseX,mouseY,x,rowY,rightWidth,contentRowH-2);
            contentHover[i]=animate(contentHover[i],over?1:0,18);if(i>=s.contents().size())continue;
            var content=s.contents().get(i);rounded(g,x,rowY,rightWidth,contentRowH-2,DashboardPalette.mix(i%2==0?palette.inset():palette.panel(),palette.hover(),contentHover[i]));
            int padding=Math.max(0,(contentRowH-19)/2);drawKey(g,content.key(),x+4,rowY+padding);String amount=exactAmount(content);
            if(rightWidth>270&&font.width(amount)<rightWidth/2) {
                clipped(g,displayName(content.key()),x+27,rowY+padding+4,rightWidth-font.width(amount)-43,palette.text());
                g.drawString(font,amount,x+rightWidth-font.width(amount)-8,rowY+padding+4,palette.accent(),false);
            } else {
                clipped(g,displayName(content.key()),x+25,rowY+padding,rightWidth-30,palette.text());
                small(g,Component.literal(amount),x+25,rowY+padding+10,rightWidth-30,palette.accent());
            }
        }
        if(s.contents().isEmpty())centered(g,tr("no_contents"),x+rightWidth/2,y+contentsY+contentRowH+3,palette.muted());
        centered(g,tr("page",s.contentPage()+1,Math.max(1,s.contentPages())),x+rightWidth/2,y+footerY+3,palette.muted());
    }
    private void searchBackground(GuiGraphics g,EditBox box,int x,int y,int w,String hint) {
        rounded(g,leftPos+x,topPos+y,w,18,box.isFocused()?palette.accent():palette.border());
        rounded(g,leftPos+x+1,topPos+y+1,w-2,16,palette.inset());box.setTextColor(palette.text());box.setTextColorUneditable(palette.muted());
        box.setHint(tr(hint).copy().withStyle(style->style.withColor(palette.muted()&0xffffff)));
    }
    private void status(GuiGraphics g,boolean online,int x,int y) {
        rounded(g,x,y,60,15,online?palette.selected():palette.inset());g.fill(x+6,y+6,x+9,y+9,online?palette.accent():palette.danger());
        clipped(g,tr(online?"status_online":"status_offline"),x+13,y+4,44,online?palette.accent():palette.danger());
    }
    private static Component location(Snapshot.DeviceInfo d) {return hasLocation(d)?Component.literal(d.pos().getX()+", "+d.pos().getY()+", "+d.pos().getZ()):tr("unknown_location");}
    private float animate(float current,float target,float speed) {return current+(target-current)*(1-(float)Math.exp(-frameDelta*speed));}
    private static float ratio(long used,long total) {return total<=0?0:(float)Math.max(0,Math.min(1,used/(double)total));}
    private int usageColor(float fraction) {return fraction>=.95F?palette.danger():fraction>=.8F?palette.warning():palette.accent();}
    private static String percent(long used,long total) {var f=NumberFormat.getPercentInstance();f.setMaximumFractionDigits(1);return f.format(total<=0?0:Math.max(0,Math.min(1,used/(double)total)));}
    private static String number(long n) {return n<0?tr("unknown").getString():NumberFormat.getIntegerInstance().format(n);}

    /** Exact long amounts never travel through double, including third-party unit conversion. */
    static String exactAmount(Snapshot.Content content) {
        AEKey key=content.key();if(key instanceof AEFluidKey)return number(content.amount())+" mB";
        int unit=Math.max(1,key.getAmountPerUnit());String symbol=key.getUnitSymbol();String suffix=symbol==null||symbol.isBlank()?"":" "+symbol;
        if(unit==1)return number(content.amount())+suffix;
        try{return BigDecimal.valueOf(content.amount()).divide(BigDecimal.valueOf(unit)).stripTrailingZeros().toPlainString()+suffix;}
        catch(ArithmeticException nonTerminating){return number(content.amount())+"/"+number(unit)+suffix;}
    }
    private Component displayName(AEKey key) {try{return AEKeyRendering.getDisplayName(key);}catch(RuntimeException unsupported){return key.getDisplayName();}}
    private void drawKey(GuiGraphics g,AEKey key,int x,int y) {try{AEKeyRendering.drawInGui(minecraft,g,x,y,key);}catch(RuntimeException unsupported){g.drawString(font,"?",x+4,y+4,palette.muted(),false);}}
    private void clipped(GuiGraphics g,Component text,int x,int y,int maxWidth,int color) {
        String value=text.getString();if(font.width(value)>maxWidth)value=font.plainSubstrByWidth(value,Math.max(0,maxWidth-font.width("…")))+"…";g.drawString(font,value,x,y,color,false);
    }
    private void small(GuiGraphics g,Component text,int x,int y,int maxWidth,int color) {
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.9F,.9F,1);clipped(g,text,0,0,Math.round(maxWidth/.9F),color);g.pose().popPose();
    }
    private void centeredSmall(GuiGraphics g,Component text,int x,int y,int color,int maxWidth) {
        float scale=Math.min(.9F,maxWidth/(float)Math.max(1,font.width(text)));g.pose().pushPose();g.pose().translate(x-font.width(text)*scale/2,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,color,false);g.pose().popPose();
    }
    private void centered(GuiGraphics g,Component text,int x,int y,int color){g.drawString(font,text,x-font.width(text)/2,y,color,false);}
    private void slot(GuiGraphics g,int x,int y,boolean enabled){rounded(g,x-1,y-1,18,18,enabled?palette.border():palette.inset());g.fill(x,y,x+16,y+16,enabled?palette.slot():palette.inset());}
    private static void rounded(GuiGraphics g,int x,int y,int w,int h,int color) {
        if(w<=0||h<=0)return;if(w<3||h<3){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+1,y,x+w-1,y+h,color);g.fill(x,y+1,x+w,y+h-1,color);
    }
    private static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g);super.render(g,mouseX,mouseY,partialTick);renderTooltip(g,mouseX,mouseY);
        var s=menu.getSnapshot();int x=mouseX-leftPos,y=mouseY-topPos;List<Component> tip=new ArrayList<>();
        if(hit(x,y,8,devicesY,190,deviceRowH*3)) {
            int index=(y-devicesY)/deviceRowH;if(index<s.devices().size())deviceTooltip(s.devices().get(index),tip);
        } else if(hit(x,y,8,cellY,180,32)&&(y>=cellY+17||(x-8)/18>=s.editableSlots())) {
            int absolute=cellOffset(s)+(x-8)/18;if(absolute<s.cellSlots()) {
                tip.add(tr("cell_details",absolute+1));var p=s.cells().stream().filter(c->c.slot()==absolute).findFirst().orElse(null);
                if(p!=null){tip.add(p.icon().isEmpty()?tr("empty_cell"):p.icon().getHoverName());tip.add(tr("bytes",number(p.usedBytes()),number(p.totalBytes())));
                    if(p.totalBytes()>0)tip.add(Component.literal(percent(p.usedBytes(),p.totalBytes())));if(!p.readable())tip.add(tr("unreadable"));}
                if((x-8)/18>=s.editableSlots())tip.add(tr("cell_readonly"));
            }
        } else if(hit(x,y,RIGHT,contentsY,rightWidth,contentRowH*6)) {
            int index=(y-contentsY)/contentRowH;if(index<s.contents().size()){
                var c=s.contents().get(index);try{tip.addAll(AEKeyRendering.getTooltip(c.key()));}catch(RuntimeException unsupported){tip.add(displayName(c.key()));}
                tip.add(Component.literal(exactAmount(c)));tip.add(Component.literal(c.key().getId().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if(hit(x,y,RIGHT,30,rightWidth,contentSearchY-32)) {
            var c=s.capacity();tip.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes())));tip.add(tr("types",number(c.usedTypes()),number(c.totalTypes())));
            if(c.totalSlots()>=0)tip.add(tr("slots",number(c.occupiedSlots()),number(c.totalSlots())));
            if(c.fluidCapacity()>=0)tip.add(tr("fluid",number(c.fluidAmount()),number(c.fluidCapacity())));
            if(c.totalSlots()>=0||c.fluidCapacity()>=0)tip.add(tr("external_capacity"));if(c.unknownCells()>0)tip.add(tr("unknown_cells",c.unknownCells()));
            if(!s.error().isEmpty())tip.add(Component.translatable(s.error()));
        } else if(hit(x,y,6,4,imageWidth-134,23)) {tip.add(s.title());var d=selectedInfo(s);if(d!=null)deviceTooltip(d,tip);}
        if(!tip.isEmpty())g.renderComponentTooltip(font,tip,mouseX,mouseY);
    }
    private void deviceTooltip(Snapshot.DeviceInfo d,List<Component> tip) {
        tip.add(d.name());tip.add(hasLocation(d)?Component.literal(d.dimension()+" · "+location(d).getString()):tr("unknown_location"));tip.add(tr("kind."+d.kind()));
        if(!d.sourceName().equals(d.name()))tip.add(tr("via_device",d.sourceName()));if(!d.face().isEmpty())tip.add(tr("connection_face",tr("direction."+d.face())));
        tip.add(tr(d.active()?"status_online":"status_offline"));
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        int x=(int)mouseX-leftPos,y=(int)mouseY-topPos;var s=menu.getSnapshot();
        if(button==0&&hit(x,y,8,devicesY,190,deviceRowH*3)){int index=(y-devicesY)/deviceRowH;if(index<s.devices().size()){request(s.devices().get(index).id(),-1,s.devicePage(),0);return true;}}
        if(button==0&&hit(x,y,8,cellY+20,180,12)&&!s.selectedDevice().isEmpty()){int cell=cellOffset(s)+(x-8)/18;if(cell<s.cellSlots()){request(s.selectedDevice(),cell,s.devicePage(),0);return true;}}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
        if(key!=256&&(deviceSearch.isFocused()||contentSearch.isFocused())){(deviceSearch.isFocused()?deviceSearch:contentSearch).keyPressed(key,scanCode,modifiers);return true;}
        return super.keyPressed(key,scanCode,modifiers);
    }
    @Override protected void slotClicked(Slot slot,int slotId,int mouseButton,ClickType type) {
        if(menu.canSendClick(slotId,type))super.slotClicked(slot,slotId,mouseButton,type);
    }
    /** Exercises the same persistent setting as the visible theme button during development captures. */
    public void setDarkThemeForTest(boolean dark) {
        if(ClientAppearance.isDark()!=dark)ClientAppearance.setDark(dark);
        if(theme!=null)theme.setMessage(themeName());
    }
    private final class DashboardButton extends Button {
        private float hover;
        DashboardButton(int x,int y,int w,int h,Component text,OnPress action){super(x,y,w,h,text,action,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float delta) {
            hover=animate(hover,isHoveredOrFocused()&&active?1:0,20);int base=DashboardPalette.mix(palette.inset(),palette.hover(),hover);
            rounded(g,getX(),getY(),getWidth(),getHeight(),isFocused()?palette.accent():palette.border());rounded(g,getX()+1,getY()+1,getWidth()-2,getHeight()-2,base);
            centeredSmall(g,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?palette.text():DashboardPalette.mix(palette.muted(),palette.inset(),.4F),getWidth()-5);
        }
    }
}
