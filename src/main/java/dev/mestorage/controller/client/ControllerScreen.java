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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** Native-size AE terminal with a directory attachment. Content tiles are inspection only. */
public final class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final ResourceLocation TERMINAL=texture("terminal"),STATES=texture("states");
    private static ResourceLocation texture(String name){return new ResourceLocation("me_storage_controller","textures/ae2_1_21/guis/"+name+".png");}
    record UiRect(int x,int y,int width,int height){double centerX(){return x+width/2.0;}double centerY(){return y+height/2.0;}}
    private final StorageTree tree=new StorageTree(this::select);
    private EditBox contentSearch,treeSearch;
    private Button theme,root,back,sort,collapse,locate,contentPrevious,contentNext,cellsPrevious,cellsNext;
    private boolean collapsed,treeOnly,narrow,treeVisible,mainVisible,sortByAmount=true,treeSearchOpen;
    private int mainX=142,visibleRows=5,gridRowOffset,cellY,inventoryY,hotbarY,treeHeight,controlPressButton=-1;
    private long searchDue,lastFrame;
    private float delta=.016F,themeMix=ClientAppearance.isDark()?1:0;
    private DashboardPalette p=DashboardPalette.blend(themeMix);
    private final float[] hover=new float[45];
    private String observedSelection="";
    private AEKey focusedKey;
    public ControllerScreen(ControllerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    static Component tr(String key,Object... args){return Component.translatable("gui.me_storage_controller."+key,args);}
    @Override protected void init(){
        var window=minecraft.getWindow();int preferred=window.calculateScale(minecraft.options.guiScale().get(),minecraft.isEnforceUnicode());
        int limit=Math.max(1,Math.min(window.getWidth()/494,window.getHeight()/324));window.setGuiScale(Math.min(preferred,limit));
        width=window.getGuiScaledWidth();height=window.getGuiScaledHeight();narrow=width<352;
        treeVisible=narrow?treeOnly:!collapsed;mainVisible=!narrow||!treeOnly;mainX=treeVisible&&!narrow?142:18;
        imageWidth=treeVisible&&!narrow?340:216;visibleRows=Math.max(2,Math.min(5,(height-150)/18));imageHeight=150+visibleRows*18;
        cellY=imageHeight-49;inventoryY=imageHeight-94;hotbarY=imageHeight-36;treeHeight=imageHeight-134;gridRowOffset=Math.min(gridRowOffset,5-visibleRows);
        String cq=contentSearch==null?"":contentSearch.getValue(),tq=treeSearch==null?"":treeSearch.getValue();super.init();
        sort=icon(0,24,18,20,16,64,tr("sort_amount"),b->{sortByAmount=!sortByAmount;((IconButton)b).sx=sortByAmount?16:0;b.setTooltip(Tooltip.create(tr(sortByAmount?"sort_amount":"sort_name")));request(0);});
        ((IconButton)sort).sx=sortByAmount?16:0;
        root=icon(0,46,18,20,160,16,tr("network_root"),b->select("",-1));
        back=icon(0,68,18,20,96,16,tr("back"),b->{var s=menu.getSnapshot();select(s.selectedCell()>=0?s.selectedDevice():"",-1);});
        collapse=icon(0,90,18,20,16,208,tr("toggle_tree"),b->{if(narrow)treeOnly=!treeOnly;else collapsed=!collapsed;rebuildWidgets();});
        theme=icon(0,112,18,20,32,64,tr("theme_toggle"),b->setDarkThemeForTest(!ClientAppearance.isDark()));
        icon(0,134,18,20,0,64,tr("tree_search"),b->{treeSearchOpen=!treeSearchOpen;if(!treeVisible){if(narrow)treeOnly=true;else collapsed=false;rebuildWidgets();}updateWidgets();if(treeSearchOpen){setFocused(treeSearch);treeSearch.setFocused(true);}});
        locate=icon(117,imageHeight-85,16,16,64,240,tr("locate"),b->{var d=menu.getSnapshot().selectedInfo();if(d!=null)DeviceHighlight.show(d.dimension(),d.pos());});
        cellsPrevious=icon(108,imageHeight-69,12,12,48,48,tr("cells_previous"),b->{var s=menu.getSnapshot();select(s.selectedDevice(),Math.max(0,offset(s)-10));});
        cellsNext=icon(123,imageHeight-69,12,12,32,48,tr("cells_next"),b->{var s=menu.getSnapshot();select(s.selectedDevice(),offset(s)+10);});
        contentPrevious=icon(mainX+173,24,12,12,0,48,tr("previous_page"),b->request(menu.getSnapshot().contentPage()-1));
        contentNext=icon(mainX+173,23+visibleRows*18-12,12,12,16,48,tr("next_page"),b->request(menu.getSnapshot().contentPage()+1));
        contentSearch=search(mainX+82,12,85,cq,"content_search_readonly",v->searchDue=System.currentTimeMillis()+250);
        treeSearch=search(25,12,110,tq,"tree_search",tree::setQuery);
        menu.layoutSlots(26,cellY,5,mainX+8,inventoryY,hotbarY,treeVisible,mainVisible);updateWidgets();
    }
    @Override public void removed(){super.removed();var w=minecraft.getWindow();w.setGuiScale(w.calculateScale(minecraft.options.guiScale().get(),minecraft.isEnforceUnicode()));}
    private Button icon(int x,int y,int w,int h,int sx,int sy,Component tooltip,Button.OnPress action){var b=new IconButton(leftPos+x,topPos+y,w,h,sx,sy,tooltip,action);b.setTooltip(Tooltip.create(tooltip));return addRenderableWidget(b);}
    private EditBox search(int x,int y,int w,String value,String hint,java.util.function.Consumer<String> response){var b=new EditBox(font,leftPos+x,topPos+y,w,10,tr(hint));b.setBordered(false);b.setMaxLength(64);b.setValue(value);b.setResponder(response);return addRenderableWidget(b);}
    private static int offset(Snapshot s){return Math.max(0,s.selectedCell())/10*10;}
    private void select(String device,int cell){searchDue=0;focusedKey=null;gridRowOffset=0;if(device.isEmpty())tree.revealRoot();menu.request(device,cell,0,0,"",contentSearch.getValue(),sortByAmount);}
    private void request(int page){var s=menu.getSnapshot();searchDue=0;focusedKey=null;gridRowOffset=0;menu.request(s.selectedDevice(),s.selectedCell(),s.devicePage(),Math.max(0,page),"",contentSearch.getValue(),sortByAmount);}
    @Override protected void containerTick(){super.containerTick();contentSearch.tick();treeSearch.tick();if(searchDue!=0&&System.currentTimeMillis()>=searchDue)request(0);var s=menu.getSnapshot();String selection=s.selectedDevice()+"/"+s.selectedCell();if(!selection.equals(observedSelection)){observedSelection=selection;focusedKey=null;if(s.selectedDevice().isEmpty())tree.revealRoot();else if(s.selectedCell()>=0)tree.revealCell(s.selectedDevice(),s.selectedCell());else tree.revealDevice(s.selectedDevice());}updateWidgets();}
    private void updateWidgets(){var s=menu.getSnapshot();contentSearch.visible=mainVisible;treeSearch.visible=treeVisible&&treeSearchOpen;back.active=!s.selectedDevice().isEmpty();locate.visible=treeVisible;locate.active=s.selectedInfo()!=null&&!s.selectedInfo().dimension().toString().equals("me_storage_controller:unknown");cellsPrevious.visible=cellsNext.visible=treeVisible&&s.cellSlots()>10;cellsPrevious.active=offset(s)>0;cellsNext.active=offset(s)+10<s.cellSlots();contentPrevious.visible=contentNext.visible=mainVisible;contentPrevious.active=s.contentPage()>0;contentNext.active=s.contentPage()+1<s.contentPages();}
    private void nativeTint(GuiGraphics g){g.setColor(1-themeMix*.6256F,1-themeMix*.6127F,1-themeMix*.5613F,1);}
    private void frame(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,p.border());g.fill(x+1,y+1,x+w-1,y+h-1,DashboardPalette.mix(0xffffffff,0xff606579,themeMix));g.fill(x+2,y+2,x+w-2,y+h-2,p.panel());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){long now=System.nanoTime();delta=lastFrame==0?.016F:Math.min(.05F,(now-lastFrame)/1_000_000_000F);lastFrame=now;themeMix=animate(themeMix,ClientAppearance.isDark()?1:0,16);p=DashboardPalette.blend(themeMix);var s=menu.getSnapshot();
        if(treeVisible){frame(g,leftPos+18,topPos+6,124,imageHeight-16);if(!treeSearchOpen)clipped(g,tr("network_storage"),leftPos+25,topPos+12,110,p.text());else field(g,treeSearch,leftPos+23,topPos+10,114,"tree_search");tree.render(g,leftPos+22,topPos+26,116,treeHeight,true,s,p,mx,my,delta);renderAttachment(g,s);}
        if(mainVisible){int x=leftPos+mainX,y=topPos;nativeTint(g);g.blit(TERMINAL,x,y+6,0,0,195,17);for(int r=0;r<visibleRows;r++)g.blit(TERMINAL,x,y+23+r*18,0,r==0?17:r==visibleRows-1?53:35,195,18);g.setColor(1,1,1,1);
            int capY=23+visibleRows*18;frame(g,x,y+capY,195,18);nativeTint(g);g.blit(TERMINAL,x,y+capY+18,0,71,195,99);g.setColor(1,1,1,1);
            clipped(g,tr("terminal_title"),x+8,y+12,69,p.text());field(g,contentSearch,x+80,y+10,89,"search_short");renderGrid(g,s,mx,my);renderCapacity(g,s,x+8,y+capY+3);clipped(g,tr("inventory_short"),x+8,y+inventoryY-9,162,p.text());
        }
    }
    private void field(GuiGraphics g,EditBox box,int x,int y,int w,String hint){g.fill(x,y,x+w,y+12,p.slot());g.fill(x,y,x+w,y+1,p.border());g.fill(x,y+11,x+w,y+12,DashboardPalette.mix(0xffffffff,0xff85859a,themeMix));box.setTextColor(p.text());if(box.getValue().isEmpty()&&!box.isFocused())clipped(g,tr(hint),box.getX(),box.getY(),box.getWidth(),p.muted());}
    private void renderGrid(GuiGraphics g,Snapshot s,int mx,int my){for(int r=0;r<visibleRows;r++)for(int col=0;col<9;col++){int i=(gridRowOffset+r)*9+col;if(i>=s.contents().size())continue;var c=s.contents().get(i);int x=leftPos+mainX+8+col*18,y=topPos+24+r*18;boolean over=hit(mx,my,x,y,16,16);hover[i]=animate(hover[i],over?1:0,18);if(hover[i]>.01F)g.fill(x,y,x+16,y+16,((int)(hover[i]*90)<<24)|0xffffff);drawKey(g,c.key(),x,y);drawAmount(g,c,x,y);if(focused(s)!=null&&focused(s).key().equals(c.key())){g.fill(x,y,x+16,y+1,0xbbffffff);g.fill(x,y+15,x+16,y+16,0xbbffffff);}}
        if(!s.error().isEmpty()||s.contents().isEmpty()){Component msg=s.error().isEmpty()?tr("no_contents"):Component.translatable(s.error());clipped(g,msg,leftPos+mainX+8,topPos+28,162,s.error().isEmpty()?p.text():p.danger());}
        int trackY=topPos+39,trackHeight=visibleRows*18-30;g.fill(leftPos+mainX+176,trackY,leftPos+mainX+182,trackY+trackHeight,p.border());int thumb=Math.max(8,trackHeight/Math.max(1,s.contentPages()));int thumbY=trackY+(trackHeight-thumb)*s.contentPage()/Math.max(1,s.contentPages()-1);g.fill(leftPos+mainX+176,thumbY,leftPos+mainX+182,thumbY+thumb,p.panel());g.fill(leftPos+mainX+176,thumbY,leftPos+mainX+182,thumbY+1,0xffeeeeee);
    }
    private void drawAmount(GuiGraphics g,Snapshot.Content c,int x,int y){String v=abbreviate(c.amount());float scale=.65F;g.pose().pushPose();g.pose().translate(x+16-font.width(v)*scale,y+10,200);g.pose().scale(scale,scale,1);g.drawString(font,v,0,0,0xffffffff,true);g.pose().popPose();}
    private static String abbreviate(long n){if(n<1000)return Long.toString(n);String[] units={"K","M","G","T","P","E"};double value=n;int i=-1;do{value/=1000;i++;}while(value>=1000&&i<units.length-1);return String.format(Locale.ROOT,value>=10?"%.0f%s":"%.1f%s",value,units[i]);}
    private Snapshot.Content focused(Snapshot s){if(focusedKey!=null)for(var c:s.contents())if(c.key().equals(focusedKey))return c;return s.contents().isEmpty()?null:s.contents().get(0);}
    private void renderAttachment(GuiGraphics g,Snapshot s){int x=leftPos+18,y=topPos;g.fill(x+6,y+imageHeight-107,x+118,y+imageHeight-106,p.border());var c=focused(s);clipped(g,c==null?s.title():displayName(c.key()),x+7,y+imageHeight-101,109,p.text());if(c!=null)fit(g,Component.literal(exactAmount(c)),x+7,y+imageHeight-90,110,p.muted());else clipped(g,tr("contents_readonly"),x+7,y+imageHeight-90,110,p.muted());
        var d=s.selectedInfo();clipped(g,d==null?tr("network_root"):Component.literal(d.pos().getX()+", "+d.pos().getY()+", "+d.pos().getZ()),x+7,y+imageHeight-76,87,p.muted());
        clipped(g,tr(s.cellSlots()>0?"cells_short":"external_short"),x+7,y+imageHeight-62,65,p.text());if(s.cellSlots()>10)clipped(g,Component.literal((offset(s)/10+1)+"/"+((s.cellSlots()+9)/10)),x+69,y+imageHeight-62,21,p.text());
        if(s.cellSlots()==0){clipped(g,tr(s.selectedDevice().isEmpty()?"select_device":"external_capacity_short"),x+7,y+cellY+5,109,p.muted());return;}
        for(int i=0;i<10;i++){int absolute=offset(s)+i,sx=leftPos+26+(i%5)*18,sy=y+cellY+(i/5)*18;nativeTint(g);g.blit(STATES,sx-1,sy-1,192,192,18,18);g.setColor(1,1,1,1);var preview=s.cells().stream().filter(v->v.slot()==absolute).findFirst().orElse(null);if(i>=s.editableSlots()&&preview!=null&&!preview.icon().isEmpty())g.renderItem(preview.icon(),sx,sy);}
    }
    private void renderCapacity(GuiGraphics g,Snapshot s,int x,int y){var c=s.capacity();long used=c.usedBytes(),total=c.totalBytes();Component label;
        if(total>=0)label=Component.literal(bytesSummary(used)+" / "+bytesSummary(total));else if(c.totalSlots()>=0){used=c.occupiedSlots();total=c.totalSlots();label=tr("slots",number(used),number(total));}else if(c.fluidCapacity()>=0){used=c.fluidAmount();total=c.fluidCapacity();label=Component.literal(number(used)+" / "+number(total)+" mB");}else label=tr("capacity_unknown");
        String types=c.usedTypes()>=0?tr("types_inline",number(c.usedTypes()),number(c.totalTypes())).getString():"";int tw=font.width(types);clipped(g,label,x,y,162-(types.isEmpty()?0:tw+5),p.text());if(!types.isEmpty())g.drawString(font,types,x+162-tw,y,p.text(),false);g.fill(x,y+10,x+160,y+12,p.slot());if(total>0&&used>0){float ratio=Math.min(1,used/(float)total);g.fill(x,y+10,x+Math.max(1,Math.round(160*ratio)),y+12,ratio>=.95F?p.danger():ratio>=.8F?p.warning():p.accent());}
    }
    private static String bytesSummary(long bytes){if(bytes<0)return tr("unknown").getString();if(bytes<1024)return bytes+" B";var f=NumberFormat.getNumberInstance();f.setMaximumFractionDigits(1);return f.format(bytes/1024.0)+" KiB";}
    private Component breadcrumb(Snapshot s){var path=tr("network_root").copy();if(s.selectedInfo()!=null)path.append(" / ").append(s.selectedInfo().dimension().toString()).append(" / ").append(s.selectedInfo().name());if(s.selectedCell()>=0)path.append(" / ").append(tr("cell",s.selectedCell()+1));return path;}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);var s=menu.getSnapshot();if(treeVisible)for(int i=0;i<10;i++){int abs=offset(s)+i,x=leftPos+26+(i%5)*18,y=topPos+cellY+(i/5)*18;if(abs==s.selectedCell()){g.fill(x-1,y-1,x+17,y,p.selected());g.fill(x-1,y+16,x+17,y+17,p.selected());}s.cells().stream().filter(v->v.slot()==abs&&v.totalBytes()>0).findFirst().ifPresent(v->{g.fill(x,y+16,x+16,y+17,p.border());int used=(int)Math.min(16,Math.round(16.0*v.usedBytes()/v.totalBytes()));if(used>0)g.fill(x,y+16,x+used,y+17,p.accent());});}
        renderTooltip(g,mx,my);var tip=new ArrayList<Component>(treeVisible?tree.tooltip(mx,my):List.of());int index=contentIndex(mx,my);if(tip.isEmpty()&&index>=0&&index<s.contents().size()){var c=s.contents().get(index);try{tip.addAll(AEKeyRendering.getTooltip(c.key()));}catch(RuntimeException e){tip.add(displayName(c.key()));}tip.add(Component.literal(exactAmount(c)));tip.add(tr("contents_readonly"));tip.add(Component.literal(c.key().getId().toString()).withStyle(ChatFormatting.DARK_GRAY));}
        int lx=mx-leftPos,ly=my-topPos;if(tip.isEmpty()&&mainVisible&&hit(lx,ly,mainX,23+visibleRows*18,195,18)){var c=s.capacity();tip.add(breadcrumb(s));tip.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes())));tip.add(tr("types",number(c.usedTypes()),number(c.totalTypes())));if(c.totalSlots()>=0)tip.add(tr("slots",number(c.occupiedSlots()),number(c.totalSlots())));if(c.fluidCapacity()>=0)tip.add(tr("fluid",number(c.fluidAmount()),number(c.fluidCapacity())));if(c.totalSlots()>=0||c.fluidCapacity()>=0)tip.add(tr("external_capacity"));if(c.unknownCells()>0)tip.add(tr("unknown_cells",c.unknownCells()));if(!s.error().isEmpty())tip.add(Component.translatable(s.error()));}
        if(tip.isEmpty()&&treeVisible&&hit(lx,ly,24,imageHeight-105,112,49)){tip.add(breadcrumb(s));var c=focused(s);if(c!=null){tip.add(displayName(c.key()));tip.add(Component.literal(exactAmount(c)));}if(s.selectedInfo()!=null){var d=s.selectedInfo();tip.add(Component.literal(d.dimension()+" · "+d.pos().toShortString()));if(!d.face().isEmpty()){tip.add(tr("via_device",d.sourceName()));tip.add(tr("connection_face",tr("direction."+d.face())));}}}
        int remote=remoteIndex(mx,my);if(tip.isEmpty()&&remote>=0&&offset(s)+remote<s.cellSlots()){int abs=offset(s)+remote;tip.add(tr("cell_details",abs+1));s.cells().stream().filter(c->c.slot()==abs).findFirst().ifPresent(c->{tip.add(c.icon().isEmpty()?tr("empty_cell"):c.icon().getHoverName());tip.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes())));});tip.add(tr(remote<s.editableSlots()?"cell_operation_hint":"cell_readonly"));}
        if(tip.isEmpty()&&mainVisible&&hit(lx,ly,mainX+6,6,72,16))tip.add(breadcrumb(s));
        if(!tip.isEmpty())g.renderComponentTooltip(font,tip,mx,my);
    }
    private int contentIndex(double mx,double my){if(!mainVisible)return -1;double x=mx-leftPos-mainX-7,y=my-topPos-23;if(x<0||x>=162||y<0||y>=visibleRows*18)return -1;return (gridRowOffset+(int)y/18)*9+(int)x/18;}
    private int remoteIndex(double mx,double my){if(!treeVisible)return -1;double x=mx-leftPos-25,y=my-topPos-cellY+1;if(x<0||x>=90||y<0||y>=36)return -1;return (int)y/18*5+(int)x/18;}
    @Override public boolean mouseClicked(double mx,double my,int button){if(treeVisible&&hit(mx,my,leftPos+22,topPos+26,116,treeHeight)){if(button==0)tree.click(mx,my);controlPressButton=button;return true;}int i=contentIndex(mx,my);if(i>=0){if(button==0&&i<menu.getSnapshot().contents().size())focusedKey=menu.getSnapshot().contents().get(i).key();controlPressButton=button;return true;}
        int remote=remoteIndex(mx,my);var snapshot=menu.getSnapshot();if(remote>=snapshot.editableSlots()&&remote>=0&&offset(snapshot)+remote<snapshot.cellSlots()){if(button==0)select(snapshot.selectedDevice(),offset(snapshot)+remote);controlPressButton=button;return true;}
        boolean widget=children().stream().anyMatch(c->c instanceof AbstractWidget w&&w.visible&&hit(mx,my,w.getX(),w.getY(),w.getWidth(),w.getHeight()));boolean handled=super.mouseClicked(mx,my,button);if(widget){controlPressButton=button;return true;}return handled;}
    @Override public boolean mouseReleased(double mx,double my,int button){if(controlPressButton==button){controlPressButton=-1;setDragging(false);if(getFocused()!=null)getFocused().mouseReleased(mx,my,button);return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(treeVisible&&tree.wheel(mx,my,amount))return true;if(contentIndex(mx,my)>=0){int row=gridRowOffset+(amount<0?1:-1);if(row>=0&&row<=5-visibleRows){gridRowOffset=row;return true;}var s=menu.getSnapshot();int page=s.contentPage()+(amount<0?1:-1);if(page>=0&&page<s.contentPages())request(page);return true;}return super.mouseScrolled(mx,my,amount);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key!=256&&(treeSearch.isFocused()&&treeSearch.visible||contentSearch.isFocused()&&contentSearch.visible)){(treeSearch.isFocused()&&treeSearch.visible?treeSearch:contentSearch).keyPressed(key,scan,mods);return true;}return super.keyPressed(key,scan,mods);}
    @Override protected void slotClicked(Slot slot,int id,int button,ClickType type){if(menu.canSendClick(id,type))super.slotClicked(slot,id,button,type);}
    private float animate(float value,float target,float speed){return value+(target-value)*(1-(float)Math.exp(-delta*speed));}
    static String number(long n){return n<0?tr("unknown").getString():NumberFormat.getIntegerInstance().format(n);}
    static String exactAmount(Snapshot.Content c){var key=c.key();if(key instanceof AEFluidKey)return number(c.amount())+" mB";int unit=Math.max(1,key.getAmountPerUnit());String u=key.getUnitSymbol(),suffix=u==null||u.isBlank()?"":" "+u;if(unit==1)return number(c.amount())+suffix;try{return BigDecimal.valueOf(c.amount()).divide(BigDecimal.valueOf(unit)).stripTrailingZeros().toPlainString()+suffix;}catch(ArithmeticException e){return number(c.amount())+"/"+number(unit)+suffix;}}
    private Component displayName(AEKey key){try{return AEKeyRendering.getDisplayName(key);}catch(RuntimeException e){return key.getDisplayName();}}
    private void drawKey(GuiGraphics g,AEKey key,int x,int y){try{AEKeyRendering.drawInGui(minecraft,g,x,y,key);}catch(RuntimeException e){g.drawString(font,"?",x+4,y+4,p.text(),false);}}
    private void clipped(GuiGraphics g,Component c,int x,int y,int w,int color){if(w<4)return;String v=c.getString();if(font.width(v)>w)v=font.plainSubstrByWidth(v,Math.max(0,w-6))+"…";g.drawString(font,v,x,y,color,false);}
    private void fit(GuiGraphics g,Component c,int x,int y,int w,int color){float scale=Math.min(1,w/(float)Math.max(1,font.width(c)));g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,c,0,0,color,false);g.pose().popPose();}
    static void rounded(GuiGraphics g,int x,int y,int w,int h,int r,int color){if(w>0&&h>0)g.fill(x,y,x+w,y+h,color);}
    private static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    public void setDarkThemeForTest(boolean dark){if(ClientAppearance.isDark()!=dark)ClientAppearance.setDark(dark);}
    private UiRect rect(AbstractWidget w){return w==null||!w.visible?null:new UiRect(w.getX(),w.getY(),w.getWidth(),w.getHeight());}
    UiRect smokeThemeRect(){return rect(theme);}UiRect smokeRootRect(){return rect(root);}UiRect smokeBackRect(){return rect(back);}UiRect smokeSortRect(){return rect(sort);}UiRect smokeCollapseRect(){return rect(collapse);}UiRect smokeContentPreviousRect(){return rect(contentPrevious);}UiRect smokeContentNextRect(){return rect(contentNext);}
    UiRect smokePanelRect(){return new UiRect(leftPos,topPos,imageWidth,imageHeight);}UiRect smokeSlotRect(int i){if(i<0||i>=menu.slots.size()||!menu.getSlot(i).isActive())return null;var s=menu.getSlot(i);return new UiRect(leftPos+s.x,topPos+s.y,16,16);}
    UiRect smokeContentRect(int i){int row=i/9-gridRowOffset;if(!mainVisible||i<0||i>=45||row<0||row>=visibleRows)return null;return new UiRect(leftPos+mainX+8+i%9*18,topPos+24+row*18,16,16);}
    AEKey smokeFocusedKey(){var c=focused(menu.getSnapshot());return c==null?null:c.key();}
    UiRect smokeTreeViewport(){return treeVisible?new UiRect(leftPos+22,topPos+26,116,treeHeight):null;}UiRect smokeDeviceRect(String id,boolean chevron){return treeVisible?tree.rect("dev:"+id,chevron):null;}UiRect smokeCellRect(String id,int cell){return treeVisible?tree.rect("cell:"+id+":"+cell,false):null;}
    void smokeExpandDevice(String id){tree.expandDevice(id);}void smokeRevealDevice(String id){tree.revealDevice(id);}void smokeRevealCell(String id,int cell){tree.revealCell(id,cell);}boolean smokeDeviceExpanded(String id){return tree.isOpen("dev:"+id);}double smokeTreeScrollOffset(){return tree.scrollOffset();}
    UiRect smokeInventoryTabRect(){return null;}UiRect smokeContentsTabRect(){return null;}UiRect smokeContentSearchRect(){return rect(contentSearch);}
    private final class IconButton extends Button {int sx,sy;float over;IconButton(int x,int y,int w,int h,int sx,int sy,Component label,OnPress action){super(x,y,w,h,label,action,DEFAULT_NARRATION);this.sx=sx;this.sy=sy;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){over=animate(over,isHoveredOrFocused()&&active?1:0,20);nativeTint(g);g.blit(STATES,getX(),getY(),getWidth(),getHeight(),176,128,18,20,256,256);g.setColor(1,1,1,1);if(over>.01F)g.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,((int)(over*45)<<24)|0xffffff);int size=Math.min(16,Math.min(getWidth()-2,getHeight()-2));g.setColor(1,1,1,active?1:.4F);g.blit(STATES,getX()+(getWidth()-size)/2,getY()+(getHeight()-size)/2,size,size,sx,sy,16,16,256,256);g.setColor(1,1,1,1);}}
}
