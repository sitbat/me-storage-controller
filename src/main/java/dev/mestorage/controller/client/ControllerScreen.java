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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** A storage file manager. Animated navigation never changes the geometry of real inventory slots. */
public final class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    record UiRect(int x,int y,int width,int height){double centerX(){return x+width/2.0;}double centerY(){return y+height/2.0;}}
    private final StorageTree tree=new StorageTree(this::select);
    private EditBox treeSearch,contentSearch;
    private Button theme,back,root,locate,contentPrevious,contentNext,cellsPrevious,cellsNext,contentsTab,inventoryTab,sort;
    private boolean compact,inventoryPage,sortByAmount=true;
    private int treeWidth,right,rightWidth,treeTop,treeHeight,dockTop,cellX,cellY,inventoryX,inventoryY,hotbarY;
    private int contentSearchY,contentsY,contentRowHeight,contentFooterY,controlPressButton=-1;
    private long searchDue,lastFrame;
    private float delta=.016F,themeMix=ClientAppearance.isDark()?1:0;
    private DashboardPalette p=DashboardPalette.blend(themeMix);
    private final float[] rowHover=new float[6];
    private String observedSelection="";

    public ControllerScreen(ControllerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    static Component tr(String key,Object... args){return Component.translatable("gui.me_storage_controller."+key,args);}
    @Override protected void init(){
        imageWidth=Math.min(width-20,640);imageHeight=Math.min(height-12,360);
        compact=imageWidth<560||imageHeight<336;
        treeWidth=compact?Math.min(132,Math.max(72,imageWidth-228)):176;
        right=treeWidth+24;rightWidth=imageWidth-right-12;treeTop=80;treeHeight=imageHeight-treeTop-25;
        dockTop=imageHeight-94;contentSearchY=compact?88:114;contentsY=compact?110:136;
        contentRowHeight=compact?16:Math.max(16,(dockTop-22-contentsY)/6);contentFooterY=compact?imageHeight-18:dockTop-17;
        String tq=treeSearch==null?"":treeSearch.getValue(),cq=contentSearch==null?"":contentSearch.getValue();
        super.init();
        locate=button(imageWidth-212,8,44,20,tr("locate"),b->{var d=menu.getSnapshot().selectedInfo();if(d!=null)DeviceHighlight.show(d.dimension(),d.pos());});
        locate.setTooltip(Tooltip.create(tr("locate")));
        back=button(imageWidth-164,8,34,20,tr("back"),b->{var s=menu.getSnapshot();select(s.selectedCell()>=0?s.selectedDevice():"",-1);});
        back.setTooltip(Tooltip.create(tr("back")));
        root=button(imageWidth-126,8,38,20,tr("root_short"),b->select("",-1));root.setTooltip(Tooltip.create(tr("network_root")));
        theme=button(imageWidth-82,8,70,20,themeName(),b->setDarkThemeForTest(!ClientAppearance.isDark()));theme.setTooltip(Tooltip.create(tr("theme_toggle")));
        treeSearch=search(12,56,treeWidth-4,tq,"tree_search",v->tree.setQuery(v));
        contentSearch=search(right,contentSearchY,rightWidth-56,cq,"content_search_readonly",v->searchDue=System.currentTimeMillis()+250);
        sort=button(imageWidth-64,contentSearchY,52,20,tr(sortByAmount?"sort_amount_short":"sort_name_short"),b->{sortByAmount=!sortByAmount;b.setMessage(tr(sortByAmount?"sort_amount_short":"sort_name_short"));updateSortTooltip();request(0);});
        updateSortTooltip();
        contentPrevious=button(right,contentFooterY,23,14,Component.literal("<"),b->request(menu.getSnapshot().contentPage()-1));
        contentNext=button(imageWidth-35,contentFooterY,23,14,Component.literal(">"),b->request(menu.getSnapshot().contentPage()+1));
        contentsTab=button(right,39,Math.min(104,rightWidth/2-2),19,tr("contents_readonly"),b->showInventory(false));
        inventoryTab=button(right+Math.min(104,rightWidth/2-2)+4,39,rightWidth-Math.min(104,rightWidth/2-2)-4,19,tr("cells_inventory"),b->showInventory(true));
        int pageY=compact?108:dockTop+73;
        cellsPrevious=button(right+146,pageY,20,15,Component.literal("<"),b->{var s=menu.getSnapshot();select(s.selectedDevice(),Math.max(0,cellOffset(s)-10));});
        cellsNext=button(right+169,pageY,20,15,Component.literal(">"),b->{var s=menu.getSnapshot();select(s.selectedDevice(),cellOffset(s)+10);});
        cellsPrevious.setTooltip(Tooltip.create(tr("cells_previous")));cellsNext.setTooltip(Tooltip.create(tr("cells_next")));
        layoutInventory();updateWidgets();
    }
    private void updateSortTooltip(){if(sort!=null)sort.setTooltip(Tooltip.create(tr(sortByAmount?"sort_amount":"sort_name")));}
    private Component themeName(){return tr(ClientAppearance.isDark()?"theme_dark":"theme_light");}
    private Button button(int x,int y,int w,int h,Component text,Button.OnPress action){return addRenderableWidget(new FlatButton(leftPos+x,topPos+y,w,h,text,action));}
    private EditBox search(int x,int y,int w,String value,String hint,java.util.function.Consumer<String> responder){
        var box=new EditBox(font,leftPos+x+6,topPos+y+6,Math.max(16,w-12),12,tr(hint));box.setBordered(false);box.setMaxLength(64);box.setValue(value);box.setResponder(responder);return addRenderableWidget(box);
    }
    private void layoutInventory(){
        cellX=right+6;cellY=compact?76:dockTop+18;inventoryX=compact?right+6:imageWidth-176;
        inventoryY=compact?141:dockTop+16;hotbarY=compact?199:dockTop+74;
        menu.layoutSlots(cellX,cellY,inventoryX,inventoryY,hotbarY,!compact||inventoryPage);
    }
    private void showInventory(boolean value){inventoryPage=value;layoutInventory();updateWidgets();}
    private void select(String device,int cell){
        if(device.isEmpty())tree.revealRoot();
        searchDue=0;menu.request(device,cell,0,0,"",contentSearch==null?"":contentSearch.getValue(),sortByAmount);
    }
    private void request(int page){var s=menu.getSnapshot();searchDue=0;menu.request(s.selectedDevice(),s.selectedCell(),s.devicePage(),Math.max(0,page),"",contentSearch.getValue(),sortByAmount);}
    private static int cellOffset(Snapshot s){return Math.max(0,s.selectedCell())/10*10;}
    @Override protected void containerTick(){
        super.containerTick();treeSearch.tick();contentSearch.tick();
        if(searchDue!=0&&System.currentTimeMillis()>=searchDue)request(0);
        var s=menu.getSnapshot();String selection=s.selectedDevice()+"/"+s.selectedCell();
        if(!observedSelection.equals(selection)){observedSelection=selection;if(!s.selectedDevice().isEmpty()){if(s.selectedCell()>=0)tree.revealCell(s.selectedDevice(),s.selectedCell());else tree.revealDevice(s.selectedDevice());}else tree.revealRoot();}
        updateWidgets();
    }
    private void updateWidgets(){
        var s=menu.getSnapshot();boolean contents=!compact||!inventoryPage,inventory=!compact||inventoryPage;
        back.active=!s.selectedDevice().isEmpty();theme.setMessage(themeName());
        locate.active=s.selectedInfo()!=null&&!s.selectedInfo().dimension().toString().equals("me_storage_controller:unknown");
        contentsTab.visible=inventoryTab.visible=compact;
        contentSearch.visible=sort.visible=contentPrevious.visible=contentNext.visible=contents;
        contentPrevious.active=s.contentPage()>0;contentNext.active=s.contentPage()+1<s.contentPages();
        cellsPrevious.visible=cellsNext.visible=inventory&&s.cellSlots()>10;
        cellsPrevious.active=cellOffset(s)>0;cellsNext.active=cellOffset(s)+10<s.cellSlots();
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        long now=System.nanoTime();delta=lastFrame==0?.016F:Math.min(.05F,(now-lastFrame)/1_000_000_000F);lastFrame=now;
        themeMix=animate(themeMix,ClientAppearance.isDark()?1:0,16);p=DashboardPalette.blend(themeMix);
        var s=menu.getSnapshot();int x=leftPos,y=topPos;
        rounded(g,x+2,y+4,imageWidth,imageHeight,6,0x33000000);rounded(g,x,y,imageWidth,imageHeight,5,p.border());rounded(g,x+1,y+1,imageWidth-2,imageHeight-2,4,p.background());
        rounded(g,x+5,y+35,treeWidth+8,imageHeight-40,4,p.panel());rounded(g,x+right-5,y+35,rightWidth+10,imageHeight-40,4,p.panel());
        g.fill(x+10,y+32,x+imageWidth-10,y+33,p.border());
        clipped(g,compact&&!s.selectedDevice().isEmpty()?s.title():title,x+12,y+13,imageWidth-236,p.text());
        clipped(g,tr("file_tree"),x+13,y+42,treeWidth-10,p.muted());
        searchBackground(g,treeSearch,12,56,treeWidth-4,"tree_search");
        tree.render(g,x+10,y+treeTop,treeWidth+1,treeHeight,compact,s,p,mouseX,mouseY,delta);
        clipped(g,s.directoryTruncated()?tr("directory_limited"):tr("devices_count",s.directoryTotalDevices()),x+13,y+imageHeight-18,treeWidth-7,s.directoryTruncated()?p.warning():p.muted());
        if(!compact||!inventoryPage){renderDetails(g,s);renderContents(g,s,mouseX,mouseY);searchBackground(g,contentSearch,right,contentSearchY,rightWidth-56,"content_search_readonly");}
        if(!compact||inventoryPage)renderInventory(g,s,mouseX,mouseY);
    }
    private void renderDetails(GuiGraphics g,Snapshot s){
        int x=leftPos+right,y=topPos;var cap=s.capacity();
        long used=cap.usedBytes(),total=cap.totalBytes();Component metric=tr("bytes",number(used),number(total));
        if(total<0&&cap.totalSlots()>=0){used=cap.occupiedSlots();total=cap.totalSlots();metric=tr("slots",number(used),number(total));}
        else if(total<0&&cap.fluidCapacity()>=0){used=cap.fluidAmount();total=cap.fluidCapacity();metric=tr("fluid",number(used),number(total));}
        int metricY=compact?65:92;
        if(!compact){
            Component breadcrumb=tr("network_root");if(s.selectedInfo()!=null)breadcrumb=breadcrumb.copy().append(" / ").append(s.selectedInfo().dimension().getPath()).append(" / ").append(s.selectedInfo().name());
            if(s.selectedCell()>=0)breadcrumb=breadcrumb.copy().append(" / ").append(tr("cell",s.selectedCell()+1));
            clipped(g,breadcrumb,x,y+42,rightWidth,p.muted());
            clipped(g,s.title(),x,y+60,rightWidth-68,p.text());
            boolean active=s.online()&&(s.selectedInfo()==null||s.selectedInfo().active());
            g.fill(x+rightWidth-62,y+62,x+rightWidth-58,y+66,active?p.accent():p.danger());
            clipped(g,tr(active?"status_online":"status_offline"),x+rightWidth-52,y+60,52,active?p.accent():p.danger());
            Component metadata=s.selectedInfo()==null?tr("contents",s.contentCount()):tr("location_info",s.selectedInfo().dimension().getPath(),s.selectedInfo().pos().getX(),s.selectedInfo().pos().getY(),s.selectedInfo().pos().getZ());
            clipped(g,metadata,x,y+76,rightWidth/2,p.muted());
            if(cap.usedTypes()>=0)rightText(g,tr("types",number(cap.usedTypes()),number(cap.totalTypes())),x+rightWidth,y+76,rightWidth/2-5,p.muted());
        }
        String percent=total>0?percent(used,total):"—";
        clipped(g,metric,x,y+metricY,rightWidth-font.width(percent)-12,p.text());g.drawString(font,percent,x+rightWidth-font.width(percent),y+metricY,p.accent(),false);
        rounded(g,x,y+metricY+12,rightWidth,4,2,p.border());
        if(total>0&&used>0){float ratio=Math.min(1,used/(float)total);rounded(g,x,y+metricY+12,Math.max(2,Math.round(rightWidth*ratio)),4,2,usageColor(ratio));}
    }
    private void renderContents(GuiGraphics g,Snapshot s,int mouseX,int mouseY){
        int x=leftPos+right,y=topPos;
        for(int i=0;i<6;i++){
            if(i>=s.contents().size())continue;var c=s.contents().get(i);int cy=y+contentsY+i*contentRowHeight;
            boolean over=hit(mouseX,mouseY,x,cy,rightWidth,contentRowHeight);rowHover[i]=animate(rowHover[i],over?1:0,18);
            rounded(g,x,cy,rightWidth,contentRowHeight-1,2,DashboardPalette.mix(i%2==0?p.inset():p.panel(),p.hover(),rowHover[i]));
            drawKey(g,c.key(),x+3,cy+Math.max(0,(contentRowHeight-16)/2));
            String amount=exactAmount(c);int aw=font.width(amount);
            clipped(g,displayName(c.key()),x+24,cy+(contentRowHeight-9)/2,Math.max(18,rightWidth-aw-36),p.text());
            rightText(g,Component.literal(amount),x+rightWidth-5,cy+(contentRowHeight-9)/2,Math.min(aw,rightWidth-50),p.text());
        }
        if(s.contents().isEmpty())clipped(g,s.error().isEmpty()?tr("no_contents"):Component.translatable(s.error()),x+8,y+contentsY+20,rightWidth-16,s.error().isEmpty()?p.muted():p.warning());
        if(!compact)clipped(g,tr("contents_readonly"),x+31,y+contentFooterY+3,rightWidth/2-62,p.muted());
        centered(g,tr("page",s.contentPage()+1,Math.max(1,s.contentPages())),x+rightWidth/2,y+contentFooterY+3,p.muted());
    }
    private void renderInventory(GuiGraphics g,Snapshot s,int mouseX,int mouseY){
        int x=leftPos,y=topPos;
        if(!compact)g.fill(x+right,y+dockTop-4,x+imageWidth-12,y+dockTop-3,p.border());
        clipped(g,tr("cell_operations"),x+cellX,y+(compact?63:dockTop+3),180,p.text());
        for(int i=0;i<10;i++){
            int sx=x+cellX+i*18,absolute=cellOffset(s)+i;boolean available=absolute<s.cellSlots();slot(g,sx,y+cellY,i<s.editableSlots());
            var preview=s.cells().stream().filter(c->c.slot()==absolute).findFirst().orElse(null);
            if(i>=s.editableSlots()&&preview!=null&&!preview.icon().isEmpty())g.renderItem(preview.icon(),sx,y+cellY);
            g.fill(sx,y+cellY+17,sx+16,y+cellY+19,p.border());
            if(preview!=null&&preview.totalBytes()>0){float r=Math.min(1,preview.usedBytes()/(float)preview.totalBytes());g.fill(sx,y+cellY+17,sx+Math.max(0,Math.round(16*r)),y+cellY+19,usageColor(r));}
            if(s.selectedCell()==absolute)rounded(g,sx,y+cellY+20,16,11,2,p.selected());
            centeredSmall(g,Component.literal(Integer.toString(absolute+1)),sx+8,y+cellY+21,available?p.text():p.muted(),15);
        }
        if(compact)clipped(g,tr("cell_page",s.cellSlots()==0?0:cellOffset(s)+1,Math.min(cellOffset(s)+10,s.cellSlots()),s.cellSlots()),x+cellX,y+113,133,p.muted());
        else{clipped(g,tr(s.editableSlots()>0?"cell_operation_hint":"contents_readonly"),x+cellX,y+cellY+37,184,p.muted());clipped(g,tr("cell_page",s.cellSlots()==0?0:cellOffset(s)+1,Math.min(cellOffset(s)+10,s.cellSlots()),s.cellSlots()),x+cellX,y+dockTop+76,133,p.muted());}
        clipped(g,tr("player_inventory"),x+inventoryX,y+(compact?129:dockTop+3),162,p.text());
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,x+inventoryX+col*18,y+inventoryY+row*18,true);
        for(int col=0;col<9;col++)slot(g,x+inventoryX+col*18,y+hotbarY,true);
    }
    private void searchBackground(GuiGraphics g,EditBox box,int x,int y,int w,String hint){rounded(g,leftPos+x,topPos+y,w,20,3,box.isFocused()?p.accent():p.border());rounded(g,leftPos+x+1,topPos+y+1,w-2,18,2,p.inset());box.setTextColor(p.text());if(box.getValue().isEmpty()&&!box.isFocused())clipped(g,tr(hint),box.getX(),box.getY(),box.getWidth(),p.muted());}
    private void slot(GuiGraphics g,int x,int y,boolean enabled){g.fill(x-1,y-1,x+17,y+17,p.border());g.fill(x,y,x+16,y+16,enabled?p.slot():p.inset());g.fill(x,y,x+16,y+1,DashboardPalette.mix(p.slot(),p.border(),.45F));}
    private int usageColor(float amount){return amount>=.95F?p.danger():amount>=.8F?p.warning():p.accent();}
    private float animate(float value,float target,float speed){return value+(target-value)*(1-(float)Math.exp(-delta*speed));}
    static String number(long value){return value<0?tr("unknown").getString():NumberFormat.getIntegerInstance().format(value);}
    private static String percent(long used,long total){var f=NumberFormat.getPercentInstance();f.setMaximumFractionDigits(1);return f.format(total<=0?0:Math.max(0,Math.min(1,used/(double)total)));}
    static String exactAmount(Snapshot.Content c){var key=c.key();if(key instanceof AEFluidKey)return number(c.amount())+" mB";int unit=Math.max(1,key.getAmountPerUnit());String u=key.getUnitSymbol(),suffix=u==null||u.isBlank()?"":" "+u;if(unit==1)return number(c.amount())+suffix;try{return BigDecimal.valueOf(c.amount()).divide(BigDecimal.valueOf(unit)).stripTrailingZeros().toPlainString()+suffix;}catch(ArithmeticException e){return number(c.amount())+"/"+number(unit)+suffix;}}
    private Component displayName(AEKey key){try{return AEKeyRendering.getDisplayName(key);}catch(RuntimeException ignored){return key.getDisplayName();}}
    private void drawKey(GuiGraphics g,AEKey key,int x,int y){try{AEKeyRendering.drawInGui(minecraft,g,x,y,key);}catch(RuntimeException ignored){g.drawString(font,"?",x+4,y+4,p.muted(),false);}}
    private void clipped(GuiGraphics g,Component text,int x,int y,int w,int color){if(w<4)return;String v=text.getString();if(font.width(v)>w)v=font.plainSubstrByWidth(v,Math.max(0,w-6))+"…";g.drawString(font,v,x,y,color,false);}
    private void rightText(GuiGraphics g,Component text,int right,int y,int max,int color){String v=text.getString();if(font.width(v)>max)v=font.plainSubstrByWidth(v,Math.max(0,max-6))+"…";g.drawString(font,v,right-font.width(v),y,color,false);}
    private void centered(GuiGraphics g,Component t,int x,int y,int color){g.drawString(font,t,x-font.width(t)/2,y,color,false);}
    private void centeredSmall(GuiGraphics g,Component t,int x,int y,int color,int max){float s=Math.min(1,max/(float)Math.max(1,font.width(t)));g.pose().pushPose();g.pose().translate(x-font.width(t)*s/2,y,0);g.pose().scale(s,s,1);g.drawString(font,t,0,0,color,false);g.pose().popPose();}
    static void rounded(GuiGraphics g,int x,int y,int w,int h,int radius,int color){if(w<=0||h<=0)return;int r=Math.min(radius,Math.min(w,h)/2);if(r<1){g.fill(x,y,x+w,y+h,color);return;}g.fill(x+r,y,x+w-r,y+h,color);g.fill(x,y+r,x+w,y+h-r,color);for(int i=1;i<r;i++){g.fill(x+r-i,y+i,x+w-r+i,y+i+1,color);g.fill(x+r-i,y+h-i-1,x+w-r+i,y+h-i,color);}}
    private static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);super.render(g,mouseX,mouseY,partial);renderTooltip(g,mouseX,mouseY);
        var s=menu.getSnapshot();var tip=new ArrayList<Component>(tree.tooltip(mouseX,mouseY));int x=mouseX-leftPos,y=mouseY-topPos;
        if(tip.isEmpty()&&(!compact||!inventoryPage)&&hit(x,y,right,contentsY,rightWidth,contentRowHeight*6)){int i=(y-contentsY)/contentRowHeight;if(i<s.contents().size()){var c=s.contents().get(i);try{tip.addAll(AEKeyRendering.getTooltip(c.key()));}catch(RuntimeException ignored){tip.add(displayName(c.key()));}tip.add(Component.literal(exactAmount(c)));tip.add(Component.literal(c.key().getId().toString()).withStyle(ChatFormatting.DARK_GRAY));tip.add(tr("contents_readonly"));}}
        if(tip.isEmpty()&&(!compact||!inventoryPage)&&hit(x,y,right,compact?62:58,rightWidth,compact?23:55)){var c=s.capacity();tip.add(s.title());tip.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes())));tip.add(tr("types",number(c.usedTypes()),number(c.totalTypes())));if(c.totalSlots()>=0)tip.add(tr("slots",number(c.occupiedSlots()),number(c.totalSlots())));if(c.totalSlots()>=0||c.fluidCapacity()>=0)tip.add(tr("external_capacity"));if(c.fluidCapacity()>=0)tip.add(tr("fluid",number(c.fluidAmount()),number(c.fluidCapacity())));if(c.unknownCells()>0)tip.add(tr("unknown_cells",c.unknownCells()));if(!s.error().isEmpty())tip.add(Component.translatable(s.error()));}
        if(tip.isEmpty()&&(!compact||inventoryPage)&&hit(x,y,cellX,cellY+17,180,15)){int absolute=cellOffset(s)+(x-cellX)/18;if(absolute<s.cellSlots()){tip.add(tr("cell_details",absolute+1));s.cells().stream().filter(c->c.slot()==absolute).findFirst().ifPresent(c->tip.add(tr("bytes",number(c.usedBytes()),number(c.totalBytes()))));tip.add(tr("cell_operation_hint"));}}
        if(!tip.isEmpty())g.renderComponentTooltip(font,tip,mouseX,mouseY);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&tree.click(mx,my)){controlPressButton=button;return true;}
        int x=(int)mx-leftPos,y=(int)my-topPos;var s=menu.getSnapshot();
        if(button==0&&(!compact||inventoryPage)&&hit(x,y,cellX,cellY+20,180,12)){int cell=cellOffset(s)+(x-cellX)/18;if(cell<s.cellSlots()){controlPressButton=button;select(s.selectedDevice(),cell);return true;}}
        boolean widget=children().stream().anyMatch(c->c instanceof AbstractWidget w&&w.visible&&w.active&&w.isMouseOver(mx,my));
        boolean handled=super.mouseClicked(mx,my,button);if(handled&&widget)controlPressButton=button;return handled;
    }
    @Override public boolean mouseReleased(double mx,double my,int button){if(controlPressButton==button){controlPressButton=-1;setDragging(false);if(getFocused()!=null)getFocused().mouseReleased(mx,my,button);return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double amount){if(tree.wheel(mx,my,amount))return true;return super.mouseScrolled(mx,my,amount);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key!=256&&(treeSearch.isFocused()||contentSearch.isFocused())){(treeSearch.isFocused()?treeSearch:contentSearch).keyPressed(key,scan,modifiers);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override protected void slotClicked(Slot slot,int id,int button,ClickType type){if(menu.canSendClick(id,type))super.slotClicked(slot,id,button,type);}
    public void setDarkThemeForTest(boolean dark){if(ClientAppearance.isDark()!=dark)ClientAppearance.setDark(dark);if(theme!=null)theme.setMessage(themeName());}

    private UiRect rect(AbstractWidget w){return w==null||!w.visible?null:new UiRect(w.getX(),w.getY(),w.getWidth(),w.getHeight());}
    UiRect smokeThemeRect(){return rect(theme);}UiRect smokeRootRect(){return rect(root);}UiRect smokeBackRect(){return rect(back);}
    UiRect smokeTreeViewport(){return new UiRect(leftPos+10,topPos+treeTop,treeWidth+1,treeHeight);}
    UiRect smokeDeviceRect(String id,boolean chevron){return tree.rect("dev:"+id,chevron);}UiRect smokeCellRect(String id,int cell){return tree.rect("cell:"+id+":"+cell,false);}
    void smokeExpandDevice(String id){tree.expandDevice(id);}void smokeRevealDevice(String id){tree.revealDevice(id);}void smokeRevealCell(String id,int cell){tree.revealCell(id,cell);}
    boolean smokeDeviceExpanded(String id){return tree.isOpen("dev:"+id);}double smokeTreeScrollOffset(){return tree.scrollOffset();}
    UiRect smokeInventoryTabRect(){return rect(inventoryTab);}UiRect smokeContentsTabRect(){return rect(contentsTab);}UiRect smokeContentSearchRect(){return contentSearch.visible?new UiRect(contentSearch.getX(),contentSearch.getY(),contentSearch.getWidth(),contentSearch.getHeight()):null;}
    private final class FlatButton extends Button{
        float hover;FlatButton(int x,int y,int w,int h,Component text,OnPress action){super(x,y,w,h,text,action,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){hover=animate(hover,isHoveredOrFocused()&&active?1:0,20);boolean selected=this==contentsTab&&!inventoryPage||this==inventoryTab&&inventoryPage;
            rounded(g,getX(),getY(),getWidth(),getHeight(),3,selected?p.accent():p.border());rounded(g,getX()+1,getY()+1,getWidth()-2,getHeight()-2,2,selected?p.selected():DashboardPalette.mix(p.panel(),p.hover(),hover));
            centeredSmall(g,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-9)/2,active?p.text():p.muted(),getWidth()-8);}
    }
}
