package dev.mestorage.controller.client;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Snapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Native-size AE terminal; item and container actions are server-authoritative. */
public final class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final String[] BYTE_UNITS={"B","KB","MB","GB","TB","PB","EB"};
    private static final ResourceLocation TERMINAL=texture("terminal"),STATES=texture("states"),TEXT_FIELD=texture("text_field");
    private ResourceLocation terminalTexture=TERMINAL,iconTexture=STATES,fieldTexture=TEXT_FIELD;
    private static ResourceLocation texture(String name){return new ResourceLocation("me_storage_controller","textures/ae2_1_21/guis/"+name+".png");}
    record UiRect(int x,int y,int width,int height){double centerX(){return x+width/2.0;}double centerY(){return y+height/2.0;}}
    private final StorageTree tree=new StorageTree(this::select);
    private EditBox contentSearch,treeSearch;
    private Button theme,root,back,sort,collapse,locate,contentPrevious,contentNext,cellsPrevious,cellsNext;
    private boolean collapsed,treeOnly,narrow,treeVisible,mainVisible,sortByAmount=true,treeSearchOpen;
    private int mainX=142,visibleRows=5,gridRowOffset,cellY,inventoryY,hotbarY,treeHeight,controlPressButton=-1;
    private int targetPage;
    private double contentWheelRemainder;
    private boolean scrollbarDragging;
    private double scrollbarGrabOffset;
    private String requestedDevice="";
    private int requestedCell=-1;
    private long searchDue,lastFrame;
    private float delta=.016F,themeMix=ClientAppearance.isDark()?1:0;
    private DashboardPalette p=DashboardPalette.blend(themeMix);
    private final float[] hover=new float[45];
    private String observedSelection="";
    private AEKey focusedKey;
    private int tooltipRenderCount;
    private String tooltipKind="none";
    private List<Component> tooltipLines=List.of();
    private Boolean smokeShiftOverride;
    public ControllerScreen(ControllerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    static Component tr(String key,Object... args){return Component.translatable("gui.me_storage_controller."+key,args);}
    @Override protected void rebuildWidgets(){
        // Screen clears the old focus before calling init, so capture it outside that lifecycle.
        boolean restoreContent=contentSearch!=null&&contentSearch.isFocused(),restoreTree=treeSearch!=null&&treeSearch.isFocused();
        super.rebuildWidgets();
        EditBox restore=restoreContent?contentSearch:restoreTree?treeSearch:null;
        if(restore!=null&&restore.visible){setFocused(restore);restore.setFocused(true);}
    }
    @Override protected void init(){
        // Screen dimensions already reflect the user's GUI scale. Never change Window or Options.
        terminalTexture=packTexture("terminal");iconTexture=packTexture("states");fieldTexture=packTexture("text_field");
        narrow=width<352;
        treeVisible=narrow?treeOnly:!collapsed;mainVisible=!narrow||!treeOnly;mainX=treeVisible&&!narrow?142:18;
        imageWidth=treeVisible&&!narrow?340:216;visibleRows=Math.max(1,Math.min(5,(height-162)/18));imageHeight=150+visibleRows*18;
        cellY=imageHeight-49;inventoryY=imageHeight-94;hotbarY=imageHeight-36;treeHeight=imageHeight-134;gridRowOffset=Math.min(gridRowOffset,5-visibleRows);
        boolean contentFocused=contentSearch!=null&&contentSearch.isFocused(),treeFocused=treeSearch!=null&&treeSearch.isFocused();
        int contentCursor=contentSearch==null?0:contentSearch.getCursorPosition(),treeCursor=treeSearch==null?0:treeSearch.getCursorPosition();
        String cq=contentSearch==null?"":contentSearch.getValue(),tq=treeSearch==null?"":treeSearch.getValue();super.init();syncContentTarget();
        sort=icon(0,24,18,20,16,64,tr("sort_amount"),b->{sortByAmount=!sortByAmount;((IconButton)b).sx=sortByAmount?16:0;b.setMessage(tr(sortByAmount?"sort_amount":"sort_name"));request(0);});
        ((IconButton)sort).sx=sortByAmount?16:0;
        root=icon(0,46,18,20,160,16,tr("network_root"),b->select("",-1));
        back=icon(0,68,18,20,96,16,tr("back"),b->{var s=menu.getSnapshot();select(s.selectedCell()>=0?s.selectedDevice():"",-1);});
        collapse=icon(0,90,18,20,16,208,tr("toggle_tree"),b->{if(narrow)treeOnly=!treeOnly;else collapsed=!collapsed;rebuildWidgets();});
        theme=icon(0,112,18,20,32,64,tr("theme_toggle"),b->setDarkThemeForTest(!ClientAppearance.isDark()));
        icon(0,134,18,20,0,64,tr("tree_search"),b->{treeSearchOpen=!treeSearchOpen;if(!treeVisible){if(narrow)treeOnly=true;else collapsed=false;rebuildWidgets();}updateWidgets();if(treeSearchOpen){setFocused(treeSearch);treeSearch.setFocused(true);}});
        locate=icon(117,imageHeight-85,16,16,64,240,tr("locate"),b->{var d=menu.getSnapshot().selectedInfo();if(d!=null)DeviceHighlight.show(d.dimension(),d.pos());});
        cellsPrevious=icon(108,imageHeight-69,12,12,48,48,tr("cells_previous"),b->{var s=menu.getSnapshot();select(s.selectedDevice(),Math.max(0,offset(s)-10));});
        cellsNext=icon(123,imageHeight-69,12,12,32,48,tr("cells_next"),b->{var s=menu.getSnapshot();select(s.selectedDevice(),offset(s)+10);});
        int arrowHeight=visibleRows==1?8:12;
        contentPrevious=icon(mainX+173,24,12,arrowHeight,0,48,tr("previous_page"),b->request(targetPage-1));
        contentNext=icon(mainX+173,23+visibleRows*18-arrowHeight,12,arrowHeight,16,48,tr("next_page"),b->request(targetPage+1));
        contentSearch=search(mainX+82,12,85,cq,"content_search_readonly",v->searchDue=System.currentTimeMillis()+250);
        treeSearch=search(25,12,110,tq,"tree_search",tree::setQuery);
        // moveCursorTo calls the text responder; restoring a caret must not schedule a new search/page reset.
        contentSearch.setCursorPosition(contentCursor);contentSearch.setHighlightPos(contentCursor);
        treeSearch.setCursorPosition(treeCursor);treeSearch.setHighlightPos(treeCursor);
        menu.layoutSlots(26,cellY,5,mainX+8,inventoryY,hotbarY,treeVisible,mainVisible);updateWidgets();
        if(contentFocused&&contentSearch.visible){setFocused(contentSearch);contentSearch.setFocused(true);}else if(treeFocused&&treeSearch.visible){setFocused(treeSearch);treeSearch.setFocused(true);}
    }
    private Button icon(int x,int y,int w,int h,int sx,int sy,Component tooltip,Button.OnPress action){return addRenderableWidget(new IconButton(leftPos+x,topPos+y,w,h,sx,sy,tooltip,action));}
    private ResourceLocation packTexture(String name){
        var resource=new ResourceLocation("ae2","textures/guis/"+name+".png");
        // File packs can override the verified 1.20 atlas regions. AE2's older bundled art must
        // not replace our modern fallback; toolbar backgrounds keep their separate 1.21 UVs.
        return minecraft.getResourceManager().getResource(resource).filter(r->r.sourcePackId().startsWith("file/")).isPresent()?resource:texture(name);
    }
    private EditBox search(int x,int y,int w,String value,String hint,java.util.function.Consumer<String> response){var b=new EditBox(font,leftPos+x,topPos+y,w,10,tr(hint));b.setBordered(false);b.setMaxLength(64);b.setValue(value);b.setResponder(response);return addRenderableWidget(b);}
    private static int offset(Snapshot s){return Math.max(0,s.selectedCell())/10*10;}
    private void select(String device,int cell){searchDue=0;focusedKey=null;targetPage=0;gridRowOffset=0;contentWheelRemainder=0;requestedDevice=device;requestedCell=cell;if(device.isEmpty())tree.revealRoot();menu.request(device,cell,0,0,"",contentSearch.getValue(),sortByAmount);}
    private void request(int page){contentWheelRemainder=0;moveContent(Math.max(0,Math.min(page,maxPage())),0,true);}
    private int maxPage(){return Math.max(0,menu.getSnapshot().contentPages()-1);}
    private int rowPositions(){return 6-visibleRows;}
    private int scrollPosition(){return targetPage*rowPositions()+gridRowOffset;}
    private int maxScrollPosition(){int lastRows=Math.max(1,(Math.max(0,menu.getSnapshot().contentCount()-maxPage()*45)+8)/9);return maxPage()*rowPositions()+Math.max(0,Math.min(5,lastRows)-visibleRows);}
    private void moveContent(int page,int row,boolean force){
        boolean pageChanged=page!=targetPage;targetPage=page;gridRowOffset=row;focusedKey=null;
        if(pageChanged||force){searchDue=0;menu.request(requestedDevice,requestedCell,menu.getSnapshot().devicePage(),targetPage,"",contentSearch.getValue(),sortByAmount);}
        updateWidgets();
    }
    private void moveContentPosition(int position){int clamped=Math.max(0,Math.min(position,maxScrollPosition()));moveContent(clamped/rowPositions(),clamped%rowPositions(),false);}
    private void syncContentTarget(){var s=menu.getSnapshot();if(s.revision()==menu.getRequestedRevision()){targetPage=s.contentPage();requestedDevice=s.selectedDevice();requestedCell=s.selectedCell();gridRowOffset=Math.max(0,Math.min(gridRowOffset,maxScrollPosition()-targetPage*rowPositions()));}}
    @Override protected void containerTick(){super.containerTick();contentSearch.tick();treeSearch.tick();syncContentTarget();if(searchDue!=0&&System.currentTimeMillis()>=searchDue)request(0);var s=menu.getSnapshot();String selection=s.selectedDevice()+"/"+s.selectedCell();if(!selection.equals(observedSelection)){observedSelection=selection;focusedKey=null;if(s.selectedDevice().isEmpty())tree.revealRoot();else if(s.selectedCell()>=0)tree.revealCell(s.selectedDevice(),s.selectedCell());else tree.revealDevice(s.selectedDevice());}updateWidgets();}
    private void updateWidgets(){var s=menu.getSnapshot();contentSearch.visible=mainVisible;treeSearch.visible=treeVisible&&treeSearchOpen;back.active=!s.selectedDevice().isEmpty();locate.visible=treeVisible;locate.active=s.selectedInfo()!=null&&!s.selectedInfo().dimension().toString().equals("me_storage_controller:unknown");cellsPrevious.visible=cellsNext.visible=treeVisible&&s.cellSlots()>10;cellsPrevious.active=offset(s)>0;cellsNext.active=offset(s)+10<s.cellSlots();contentPrevious.visible=contentNext.visible=mainVisible;contentPrevious.active=targetPage>0;contentNext.active=targetPage<maxPage();}
    private void nativeTint(GuiGraphics g){nativeTint(g,1);}
    private void nativeTint(GuiGraphics g,float alpha){g.setColor(1-themeMix*.6256F,1-themeMix*.6127F,1-themeMix*.5613F,alpha);}
    private void frame(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,p.border());g.fill(x+1,y+1,x+w-1,y+h-1,DashboardPalette.mix(0xfff2f2f2,0xff606579,themeMix));g.fill(x+2,y+2,x+w-2,y+h-2,p.panel());}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){long now=System.nanoTime();delta=lastFrame==0?.016F:Math.min(.05F,(now-lastFrame)/1_000_000_000F);lastFrame=now;themeMix=animate(themeMix,ClientAppearance.isDark()?1:0,16);p=DashboardPalette.blend(themeMix);var s=menu.getSnapshot();
        if(treeVisible){frame(g,leftPos+18,topPos+6,124,imageHeight-16);if(!treeSearchOpen)clipped(g,tr("network_storage"),leftPos+25,topPos+12,110,p.text());else field(g,treeSearch,leftPos+23,topPos+10,114,"tree_search");tree.render(g,leftPos+22,topPos+26,116,treeHeight,true,s,p,mx,my,delta);renderAttachment(g,s);}
        if(mainVisible){int x=leftPos+mainX,y=topPos;nativeTint(g);g.blit(terminalTexture,x,y+6,0,0,195,17);for(int r=0;r<visibleRows;r++)g.blit(terminalTexture,x,y+23+r*18,0,r==0?17:r==visibleRows-1?53:35,195,18);g.setColor(1,1,1,1);
            int capY=23+visibleRows*18;frame(g,x,y+capY,195,18);nativeTint(g);g.blit(terminalTexture,x,y+capY+18,0,71,195,99);g.setColor(1,1,1,1);
            clipped(g,tr("terminal_title"),x+8,y+12,69,p.text());field(g,contentSearch,x+80,y+10,89,"search_short");renderGrid(g,s,mx,my);renderCapacity(g,s,x+8,y+capY+3);clipped(g,tr("inventory_short"),x+8,y+inventoryY-9,162,p.text());
        }
    }
    private void field(GuiGraphics g,EditBox box,int x,int y,int w,String hint){int sourceY=box.isFocused()?24:0;nativeTint(g);g.blit(fieldTexture,x,y,0,sourceY,1,12,128,128);g.blit(fieldTexture,x+1,y,1,sourceY,w-2,12,128,128);g.blit(fieldTexture,x+w-1,y,127,sourceY,1,12,128,128);g.setColor(1,1,1,1);box.setTextColor(p.text());if(box.getValue().isEmpty()&&!box.isFocused())clipped(g,tr(hint),box.getX(),box.getY(),box.getWidth(),p.text());}
    private void renderGrid(GuiGraphics g,Snapshot s,int mx,int my){for(int r=0;r<visibleRows;r++)for(int col=0;col<9;col++){int i=(gridRowOffset+r)*9+col;if(i>=s.contents().size())continue;var c=s.contents().get(i);int x=leftPos+mainX+8+col*18,y=topPos+24+r*18;boolean over=hit(mx,my,x,y,16,16);hover[i]=animate(hover[i],over?1:0,18);if(hover[i]>.01F)g.fill(x,y,x+16,y+16,((int)(hover[i]*90)<<24)|0xffffff);drawKey(g,c.key(),x,y);drawAmount(g,c,x,y);if(focused(s)!=null&&focused(s).key().equals(c.key())){g.fill(x,y,x+16,y+1,0xbbffffff);g.fill(x,y+15,x+16,y+16,0xbbffffff);}}
        if(!s.error().isEmpty()||s.contents().isEmpty()){Component msg=s.error().isEmpty()?tr("no_contents"):Component.translatable(s.error());clipped(g,msg,leftPos+mainX+8,topPos+28,162,s.error().isEmpty()?p.text():p.danger());}
        var track=scrollbarRect();if(track!=null){int thumb=scrollbarThumbHeight(track),thumbY=scrollbarThumbY(track);g.fill(track.x(),track.y(),track.x()+track.width(),track.y()+track.height(),p.border());g.fill(track.x(),thumbY,track.x()+track.width(),thumbY+thumb,p.inset());int shine=DashboardPalette.mix(0xfff2f2f2,0xff858b9e,themeMix);g.fill(track.x(),thumbY,track.x()+track.width()-1,thumbY+1,shine);g.fill(track.x(),thumbY,track.x()+1,thumbY+thumb-1,shine);g.fill(track.x()+track.width()-1,thumbY,track.x()+track.width(),thumbY+thumb,p.border());g.fill(track.x(),thumbY+thumb-1,track.x()+track.width(),thumbY+thumb,p.border());}
    }
    private void drawAmount(GuiGraphics g,Snapshot.Content c,int x,int y){String v=abbreviate(c.amount());float scale=.65F;g.pose().pushPose();g.pose().translate(x+16-font.width(v)*scale,y+10,200);g.pose().scale(scale,scale,1);g.drawString(font,v,0,0,0xffffffff,true);g.pose().popPose();}
    private static String abbreviate(long n){if(n<1000)return Long.toString(n);String[] units={"K","M","G","T","P","E"};double value=n;int i=-1;do{value/=1000;i++;}while(value>=1000&&i<units.length-1);return String.format(Locale.ROOT,value>=10?"%.0f%s":"%.1f%s",value,units[i]);}
    private Snapshot.Content focused(Snapshot s){if(focusedKey!=null)for(var c:s.contents())if(c.key().equals(focusedKey))return c;return s.contents().isEmpty()?null:s.contents().get(0);}
    private void renderAttachment(GuiGraphics g,Snapshot s){int x=leftPos+18,y=topPos;g.fill(x+6,y+imageHeight-107,x+118,y+imageHeight-106,p.border());var c=focused(s);clipped(g,c==null?s.title():displayName(c.key()),x+7,y+imageHeight-101,109,p.text());if(c!=null)fit(g,Component.literal(exactAmount(c)),x+7,y+imageHeight-90,110,p.muted());
        var d=s.selectedInfo();clipped(g,d==null?tr("network_root"):Component.literal(d.pos().getX()+", "+d.pos().getY()+", "+d.pos().getZ()),x+7,y+imageHeight-76,87,p.muted());
        clipped(g,tr(s.cellSlots()>0?"cells_short":"external_short"),x+7,y+imageHeight-62,65,p.text());if(s.cellSlots()>10)clipped(g,Component.literal((offset(s)/10+1)+"/"+((s.cellSlots()+9)/10)),x+69,y+imageHeight-62,21,p.text());
        if(s.cellSlots()==0){clipped(g,tr(s.selectedDevice().isEmpty()?"select_device":"external_capacity_short"),x+7,y+cellY+5,109,p.muted());return;}
        for(int i=0;i<10;i++){int absolute=offset(s)+i,sx=leftPos+26+(i%5)*18,sy=y+cellY+(i/5)*18;nativeTint(g);g.blit(iconTexture,sx-1,sy-1,192,192,18,18);g.setColor(1,1,1,1);var preview=s.cells().stream().filter(v->v.slot()==absolute).findFirst().orElse(null);if(i>=s.editableSlots()&&preview!=null&&!preview.icon().isEmpty())g.renderItem(preview.icon(),sx,sy);}
    }
    private void renderCapacity(GuiGraphics g,Snapshot s,int x,int y){var c=s.capacity();long used=c.usedBytes(),total=c.totalBytes();Component label;
        if(total>=0)label=Component.literal(bytesSummary(used)+" / "+bytesSummary(total));else if(c.totalSlots()>=0){used=c.occupiedSlots();total=c.totalSlots();label=tr("slots",number(used),number(total));}else if(c.fluidCapacity()>=0){used=c.fluidAmount();total=c.fluidCapacity();label=Component.literal(number(used)+" / "+number(total)+" mB");}else label=tr("capacity_unknown");
        String types=c.usedTypes()>=0?tr("types_inline",number(c.usedTypes()),number(c.totalTypes())).getString():"";int tw=Math.min(76,font.width(types));fit(g,label,x,y,162-(types.isEmpty()?0:tw+5),p.text());if(!types.isEmpty())fit(g,Component.literal(types),x+162-tw,y,tw,p.text());g.fill(x,y+10,x+160,y+12,p.slot());if(total>0&&used>0){float ratio=Math.min(1,used/(float)total);g.fill(x,y+10,x+Math.max(1,Math.round(160*ratio)),y+12,ratio>=.95F?p.danger():ratio>=.8F?p.warning():p.accent());}
    }
    private static String bytesSummary(long bytes){
        if(bytes<0)return tr("unknown").getString();
        if(bytes<1024)return bytes+" B";
        double amount=bytes;int unit=0;
        while(amount>=1024 && unit<BYTE_UNITS.length-1){amount/=1024;unit++;}
        // Promote a rounded 1024 to the next unit as well, keeping the summary compact at boundaries.
        if(Math.round(amount*10)>=10240 && unit<BYTE_UNITS.length-1){amount/=1024;unit++;}
        var format=NumberFormat.getNumberInstance();format.setGroupingUsed(false);format.setMaximumFractionDigits(1);
        return format.format(amount)+" "+BYTE_UNITS[unit];
    }
    private Component breadcrumb(Snapshot s){var path=tr("network_root").copy();if(s.selectedInfo()!=null)path.append(" / ").append(s.selectedInfo().dimension().toString()).append(" / ").append(s.selectedInfo().name());if(s.selectedCell()>=0)path.append(" / ").append(tr("cell",s.selectedCell()+1));return path;}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);super.render(g,mx,my,partial);var s=menu.getSnapshot();if(treeVisible)for(int i=0;i<10;i++){int abs=offset(s)+i,x=leftPos+26+(i%5)*18,y=topPos+cellY+(i/5)*18;if(abs==s.selectedCell()){g.fill(x-1,y-1,x+17,y,p.selected());g.fill(x-1,y+16,x+17,y+17,p.selected());}s.cells().stream().filter(v->v.slot()==abs&&v.totalBytes()>0).findFirst().ifPresent(v->{g.fill(x,y+16,x+16,y+17,p.border());int used=(int)Math.min(16,Math.round(16.0*v.usedBytes()/v.totalBytes()));if(used>0)g.fill(x,y+16,x+used,y+17,p.accent());});}
        renderControllerTooltip(g,mx,my);
    }
    /** A single tooltip decision per frame: controls, real slots, content tiles, then metadata. */
    private void renderControllerTooltip(GuiGraphics g,int mx,int my){
        tooltipRenderCount=0;tooltipKind="none";tooltipLines=List.of();
        for(var child:children())if(child instanceof IconButton button&&button.visible&&hit(mx,my,button.getX(),button.getY(),button.getWidth(),button.getHeight())){
            drawTooltip(g,List.of(button.getMessage()),ItemStack.EMPTY,mx,my,"widget");return;
        }
        // Match vanilla container behavior: carried items suppress item and metadata tooltips.
        if(!menu.getCarried().isEmpty())return;
        var s=menu.getSnapshot();
        if(hoveredSlot!=null&&hoveredSlot.isActive()&&hoveredSlot.hasItem()){
            ItemStack stack=hoveredSlot.getItem();var lines=new ArrayList<Component>(getTooltipFromContainerItem(stack));
            if(hoveredSlot.index<10)appendCellTooltip(lines,s,hoveredSlot.index,false);
            drawTooltip(g,lines,stack,mx,my,hoveredSlot.index<10?"cell":"inventory");return;
        }
        var tip=new ArrayList<Component>();int remote=remoteIndex(mx,my);
        if(remote>=0&&offset(s)+remote<s.cellSlots()){
            appendCellTooltip(tip,s,remote,true);drawTooltip(g,tip,ItemStack.EMPTY,mx,my,"cell");return;
        }
        int index=contentIndex(mx,my);
        if(index>=0){
            if(index<s.contents().size()){
                var c=s.contents().get(index);try{tip.addAll(AEKeyRendering.getTooltip(c.key()));}catch(RuntimeException e){tip.add(displayName(c.key()));}
                tip.add(Component.literal(exactAmount(c)));
                tip.add(Component.literal(c.key().getId().toString()).withStyle(ChatFormatting.DARK_GRAY));
                ItemStack icon=c.key() instanceof AEItemKey key?key.toStack():ItemStack.EMPTY;
                drawTooltip(g,tip,icon,mx,my,"content");
            }
            return;
        }
        int lx=mx-leftPos,ly=my-topPos;
        if(treeVisible)tip.addAll(tree.tooltip(mx,my));
        if(!tip.isEmpty()){drawTooltip(g,tip,ItemStack.EMPTY,mx,my,"tree");return;}
        if(mainVisible&&hit(lx,ly,mainX,23+visibleRows*18,195,18)){
            var c=s.capacity();tip.add(breadcrumb(s));tip.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes())));tip.add(tr("types",number(c.usedTypes()),number(c.totalTypes())));
            if(c.totalSlots()>=0)tip.add(tr("slots",number(c.occupiedSlots()),number(c.totalSlots())));if(c.fluidCapacity()>=0)tip.add(tr("fluid",number(c.fluidAmount()),number(c.fluidCapacity())));
            if(c.totalSlots()>=0||c.fluidCapacity()>=0)tip.add(tr("external_capacity"));if(c.unknownCells()>0)tip.add(tr("unknown_cells",c.unknownCells()));if(!s.error().isEmpty())tip.add(Component.translatable(s.error()));
        }else if(treeVisible&&hit(lx,ly,24,imageHeight-105,112,49)){
            tip.add(breadcrumb(s));var c=focused(s);if(c!=null){tip.add(displayName(c.key()));tip.add(Component.literal(exactAmount(c)));}
            if(s.selectedInfo()!=null){var d=s.selectedInfo();tip.add(Component.literal(d.dimension()+" · "+d.pos().toShortString()));if(!d.face().isEmpty()){tip.add(tr("via_device",d.sourceName()));tip.add(tr("connection_face",tr("direction."+d.face())));}}
        }else if(mainVisible&&hit(lx,ly,mainX+6,6,72,16))tip.add(breadcrumb(s));
        if(!tip.isEmpty())drawTooltip(g,tip,ItemStack.EMPTY,mx,my,"metadata");
    }
    private void appendCellTooltip(List<Component> lines,Snapshot s,int remote,boolean includeName){
        int absolute=offset(s)+remote;
        // Actual stacks already provide AE's rich name/capacity lines. Preview icons are
        // stripped stacks, so only those need snapshot-backed capacity text.
        if(includeName)s.cells().stream().filter(c->c.slot()==absolute).findFirst().ifPresent(c->{lines.add(c.icon().isEmpty()?tr("empty_cell"):c.icon().getHoverName());lines.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes())));});
        lines.add(tr("cell_slot",absolute+1));
    }
    private void drawTooltip(GuiGraphics g,List<Component> lines,ItemStack stack,int mx,int my,String kind){
        tooltipRenderCount++;tooltipKind=kind;tooltipLines=List.copyOf(lines);
        g.renderTooltip(font,lines,stack.isEmpty()?Optional.empty():stack.getTooltipImage(),stack,mx,my);
    }
    private int contentIndex(double mx,double my){if(!mainVisible)return -1;double x=mx-leftPos-mainX-7,y=my-topPos-23;if(x<0||x>=162||y<0||y>=visibleRows*18)return -1;return (gridRowOffset+(int)y/18)*9+(int)x/18;}
    private boolean contentScrollArea(double mx,double my){return mainVisible&&hit(mx,my,leftPos+mainX+7,topPos+23,180,visibleRows*18);}
    private UiRect scrollbarRect(){int arrow=visibleRows==1?8:12,start=26+arrow,end=21+visibleRows*18-arrow;return mainVisible&&end-start>=4?new UiRect(leftPos+mainX+174,topPos+start,10,end-start):null;}
    private int scrollbarThumbHeight(UiRect track){return Math.min(track.height(),Math.max(8,track.height()/Math.max(1,maxScrollPosition()+1)));}
    private int scrollbarThumbY(UiRect track){return track.y()+(int)Math.round((track.height()-scrollbarThumbHeight(track))*Math.min(scrollPosition(),maxScrollPosition())/(double)Math.max(1,maxScrollPosition()));}
    private void dragScrollbar(double my){var track=scrollbarRect();if(track==null)return;double available=track.height()-scrollbarThumbHeight(track);if(available<=0)return;moveContentPosition((int)Math.round((my-track.y()-scrollbarGrabOffset)/available*maxScrollPosition()));}
    private int remoteIndex(double mx,double my){if(!treeVisible)return -1;double x=mx-leftPos-25,y=my-topPos-cellY+1;if(x<0||x>=90||y<0||y>=36)return -1;return (int)y/18*5+(int)x/18;}
    @Override public boolean mouseClicked(double mx,double my,int button){syncContentTarget();var track=scrollbarRect();if(track!=null&&hit(mx,my,track.x(),track.y(),track.width(),track.height())){controlPressButton=button;if(button==0){scrollbarDragging=true;contentWheelRemainder=0;int thumbY=scrollbarThumbY(track),thumbHeight=scrollbarThumbHeight(track);scrollbarGrabOffset=my>=thumbY&&my<thumbY+thumbHeight?my-thumbY:thumbHeight/2.0;dragScrollbar(my);}return true;}if(treeVisible&&hit(mx,my,leftPos+22,topPos+26,116,treeHeight)){if(button==0)tree.click(mx,my);controlPressButton=button;return true;}int i=contentIndex(mx,my);if(i>=0){
            var contents=menu.getSnapshot().contents();AEKey key=i<contents.size()?contents.get(i).key():null;if(key!=null)focusedKey=key;
            if((button==0||button==1)&&(!menu.getCarried().isEmpty()||key!=null))menu.requestContent(i,button,smokeShiftOverride!=null?smokeShiftOverride:hasShiftDown());
            controlPressButton=button;return true;}
        int remote=remoteIndex(mx,my);var snapshot=menu.getSnapshot();if(remote>=snapshot.editableSlots()&&remote>=0&&offset(snapshot)+remote<snapshot.cellSlots()){if(button==0)select(snapshot.selectedDevice(),offset(snapshot)+remote);controlPressButton=button;return true;}
        boolean widget=children().stream().anyMatch(c->c instanceof AbstractWidget w&&w.visible&&hit(mx,my,w.getX(),w.getY(),w.getWidth(),w.getHeight()));boolean handled=super.mouseClicked(mx,my,button);if(widget){controlPressButton=button;return true;}return handled;}
    @Override public boolean mouseReleased(double mx,double my,int button){if(controlPressButton==button){controlPressButton=-1;scrollbarDragging=false;setDragging(false);if(getFocused()!=null)getFocused().mouseReleased(mx,my,button);return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(scrollbarDragging&&button==0){dragScrollbar(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(treeVisible&&tree.wheel(mx,my,amount))return true;if(contentScrollArea(mx,my)){syncContentTarget();if(Double.isFinite(amount)){contentWheelRemainder-=amount;int steps=(int)contentWheelRemainder;if(steps!=0){contentWheelRemainder-=steps;moveContentPosition((int)Math.max(0,Math.min((long)maxScrollPosition(),(long)scrollPosition()+steps)));}if(scrollPosition()==0&&contentWheelRemainder<0||scrollPosition()==maxScrollPosition()&&contentWheelRemainder>0)contentWheelRemainder=0;}return true;}return super.mouseScrolled(mx,my,amount);}
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
    UiRect smokeContentScrollRect(){return mainVisible?new UiRect(leftPos+mainX+7,topPos+23,180,visibleRows*18):null;}
    UiRect smokeScrollbarRect(){return scrollbarRect();}
    int smokeContentScrollPosition(){return scrollPosition();}int smokeContentScrollMax(){return maxScrollPosition();}
    int smokeContentTargetPage(){return targetPage;}int smokeContentAcknowledgedPage(){return menu.getSnapshot().contentPage();}
    int smokeContentRowOffset(){return gridRowOffset;}int smokeContentVisibleRows(){return visibleRows;}
    boolean smokeContentPending(){return menu.getSnapshot().revision()!=menu.getRequestedRevision();}
    Map<String,String> smokeGuiTextureSources(){return Map.of("terminal",textureSource(terminalTexture),"icons",textureSource(iconTexture),"text_field",textureSource(fieldTexture),"button_background",textureSource(STATES));}
    private String textureSource(ResourceLocation texture){return texture+" | "+minecraft.getResourceManager().getResource(texture).map(r->r.sourcePackId()).orElse("missing");}
    AEKey smokeFocusedKey(){var c=focused(menu.getSnapshot());return c==null?null:c.key();}
    int smokeTooltipRenderCount(){return tooltipRenderCount;}String smokeTooltipKind(){return tooltipKind;}List<String> smokeTooltipText(){return tooltipLines.stream().map(Component::getString).toList();}
    String smokeRootLabel(){return root.getMessage().getString();}
    void smokeContentClick(int index,int button,boolean shift){
        if(net.minecraftforge.fml.loading.FMLEnvironment.production)throw new IllegalStateException("Development smoke helper is disabled in production");
        var target=smokeContentRect(index);if(target==null)throw new IllegalArgumentException("Content tile is not visible");
        smokeShiftOverride=shift;try{mouseClicked(target.centerX(),target.centerY(),button);mouseReleased(target.centerX(),target.centerY(),button);}finally{smokeShiftOverride=null;}
    }
    UiRect smokeTreeViewport(){return treeVisible?new UiRect(leftPos+22,topPos+26,116,treeHeight):null;}UiRect smokeDeviceRect(String id,boolean chevron){return treeVisible?tree.rect("dev:"+id,chevron):null;}UiRect smokeCellRect(String id,int cell){return treeVisible?tree.rect("cell:"+id+":"+cell,false):null;}
    void smokeExpandDevice(String id){tree.expandDevice(id);}void smokeRevealDevice(String id){tree.revealDevice(id);}void smokeRevealCell(String id,int cell){tree.revealCell(id,cell);}boolean smokeDeviceExpanded(String id){return tree.isOpen("dev:"+id);}double smokeTreeScrollOffset(){return tree.scrollOffset();}
    UiRect smokeInventoryTabRect(){return null;}UiRect smokeContentsTabRect(){return null;}UiRect smokeContentSearchRect(){return rect(contentSearch);}
    private final class IconButton extends Button {int sx,sy;float over;IconButton(int x,int y,int w,int h,int sx,int sy,Component label,OnPress action){super(x,y,w,h,label,action,DEFAULT_NARRATION);this.sx=sx;this.sy=sy;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
            over=animate(over,isHoveredOrFocused()&&active?1:0,20);nativeTint(g);
            g.blit(STATES,getX(),getY(),getWidth(),getHeight(),176,128,18,20,256,256);
            if(over>.01F){com.mojang.blaze3d.systems.RenderSystem.enableBlend();nativeTint(g,over);g.blit(STATES,getX(),getY(),getWidth(),getHeight(),isHovered()?212:194,128,18,20,256,256);}
            int size=Math.min(16,Math.min(getWidth()-2,getHeight()-2));int iconY=getHeight()==20&&getWidth()==18?1:(getHeight()-size)/2;
            g.setColor(1,1,1,active?1:.4F);g.blit(iconTexture,getX()+(getWidth()-size)/2,getY()+iconY,size,size,sx,sy,16,16,256,256);g.setColor(1,1,1,1);
        }}
}
