package dev.mestorage.controller.client;

import java.util.*;
import java.util.function.BiConsumer;
import dev.mestorage.controller.network.Snapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Local expandable directory; contents and inventory access still come from the server menu. */
final class StorageTree {
    record Row(String key,String parent,String device,int slot,int depth,Component label,ItemStack icon,
               boolean branch,float visibility,double top,double height) {}
    private record Motion(float from,float to,long started) {
        float value(){float t=Math.min(1,(System.nanoTime()-started)/160_000_000F);t=t*t*(3-2*t);return from+(to-from)*t;}
    }
    private final Map<String,Boolean> opened=new HashMap<>();
    private final Map<String,Motion> motions=new HashMap<>();
    private final Map<String,Float> hover=new HashMap<>();
    private List<Row> rows=List.of();
    private Map<String,Snapshot.CellPreview> cellPreviews=Map.of();
    private Snapshot snapshot=Snapshot.empty();
    private String query="",revealKey;
    private int x,y,width,height,rowHeight=20;
    private double scroll,totalHeight;
    private boolean layoutDirty=true;
    private final BiConsumer<String,Integer> select;
    StorageTree(BiConsumer<String,Integer> select){this.select=select;}
    private static Component tr(String k,Object... a){return Component.translatable("gui.me_storage_controller."+k,a);}
    private boolean defaultOpen(String key){return key.equals("root")||key.startsWith("dim:");}
    boolean isOpen(String key){return opened.getOrDefault(key,defaultOpen(key));}
    private float amount(String key){return query.isBlank()?motions.containsKey(key)?motions.get(key).value():isOpen(key)?1:0:1;}
    private void setOpen(String key,boolean value){
        if(isOpen(key)==value)return;
        float old=amount(key);opened.put(key,value);motions.put(key,new Motion(old,value?1:0,System.nanoTime()));layoutDirty=true;
    }
    void expandDevice(String id){setOpen("root",true);setOpen("dev:"+id,true);
        snapshot.directory().stream().filter(d->d.device().id().equals(id)).findFirst().ifPresent(d->setOpen("dim:"+d.device().dimension(),true));}
    void revealCell(String id,int slot){expandDevice(id);revealKey="cell:"+id+":"+slot;}
    void revealRoot(){setOpen("root",true);revealKey="root";scroll=0;}
    void revealDevice(String id){setOpen("root",true);snapshot.directory().stream().filter(d->d.device().id().equals(id)).findFirst().ifPresent(d->setOpen("dim:"+d.device().dimension(),true));revealKey="dev:"+id;}
    double scrollOffset(){return scroll;}
    void setQuery(String value){String normalized=value.toLowerCase(Locale.ROOT).trim();if(!query.equals(normalized)){query=normalized;scroll=0;layoutDirty=true;}}
    private boolean matches(String value){return value.toLowerCase(Locale.ROOT).contains(query);}
    private Component dimension(String id){return switch(id){case "minecraft:overworld"->tr("dimension_overworld");case "minecraft:the_nether"->tr("dimension_nether");case "minecraft:the_end"->tr("dimension_end");default->Component.literal(id);};}
    private Component cellLabel(Snapshot.CellPreview cell){return Component.literal((cell.slot()+1)+"  ").append(cell.icon().isEmpty()?tr("empty_cell"):cell.icon().getHoverName());}
    private void layout(){
        boolean moving=motions.values().stream().anyMatch(m->System.nanoTime()-m.started()<160_000_000L);
        if(!layoutDirty&&!moving&&revealKey==null)return;
        var built=new ArrayList<Row>();var previews=new HashMap<String,Snapshot.CellPreview>();double top=0;
        built.add(new Row("root","","",-1,0,tr("network_root"),ItemStack.EMPTY,true,1,top,rowHeight));top+=rowHeight;
        var groups=new TreeMap<String,List<Snapshot.DirectoryEntry>>();
        for(var entry:snapshot.directory())groups.computeIfAbsent(entry.device().dimension().toString(),ignored->new ArrayList<>()).add(entry);
        float rootVisibility=amount("root");
        for(var group:groups.entrySet()){
            String dimKey="dim:"+group.getKey();Component dimName=dimension(group.getKey());boolean dimMatches=matches(group.getKey()+" "+dimName.getString());
            var entries=group.getValue().stream().filter(e->query.isBlank()||dimMatches||matches(e.device().name().getString()+" "+e.device().id())||e.cells().stream().anyMatch(c->matches(cellLabel(c).getString()))).toList();
            if(entries.isEmpty()||rootVisibility<=.001F)continue;
            built.add(new Row(dimKey,"root","",-1,1,dimName,ItemStack.EMPTY,true,rootVisibility,top,rowHeight*rootVisibility));top+=rowHeight*rootVisibility;
            float deviceVisibility=rootVisibility*amount(dimKey);
            for(var entry:entries){
                if(deviceVisibility<=.001F)continue;var d=entry.device();String deviceKey="dev:"+d.id();
                built.add(new Row(deviceKey,dimKey,d.id(),-1,2,d.name(),d.icon(),entry.cellSlots()>0,deviceVisibility,top,rowHeight*deviceVisibility));top+=rowHeight*deviceVisibility;
                float cellVisibility=deviceVisibility*amount(deviceKey);
                boolean allCells=query.isBlank()||dimMatches||matches(d.name().getString()+" "+d.id());
                for(var cell:entry.cells()){
                    if(cellVisibility<=.001F||!allCells&&!matches(cellLabel(cell).getString()))continue;
                    String cellKey="cell:"+d.id()+":"+cell.slot();previews.put(cellKey,cell);
                    built.add(new Row(cellKey,deviceKey,d.id(),cell.slot(),3,cellLabel(cell),cell.icon(),false,cellVisibility,top,rowHeight*cellVisibility));top+=rowHeight*cellVisibility;
                }
            }
        }
        rows=built;cellPreviews=previews;totalHeight=top;
        // An earlier expanded branch can keep pushing the requested row down. Track it until
        // every expansion has settled, instead of clearing the reveal on its first visible frame.
        if(revealKey!=null){for(var row:rows)if(row.key.equals(revealKey)&&row.visibility>.95F){if(row.top<scroll)scroll=row.top;else if(row.top+row.height>scroll+height)scroll=row.top+row.height-height;if(!moving)revealKey=null;break;}}
        if(!moving&&revealKey!=null&&rows.stream().noneMatch(row->row.key.equals(revealKey)))revealKey=null;
        scroll=Math.max(0,Math.min(scroll,Math.max(0,totalHeight-height)));
        layoutDirty=moving; // Build the settled final geometry once after an animation ends.
    }
    void render(GuiGraphics g,int x,int y,int width,int height,boolean compact,Snapshot data,DashboardPalette p,int mouseX,int mouseY,float dt){
        int nextRowHeight=compact?18:20;
        if(snapshot.directory()!=data.directory()||this.width!=width||this.height!=height||rowHeight!=nextRowHeight)layoutDirty=true;
        this.x=x;this.y=y;this.width=width;this.height=height;this.snapshot=data;rowHeight=nextRowHeight;layout();
        var font=Minecraft.getInstance().font;
        g.enableScissor(x,y,x+width,y+height);
        for(var row:rows){
            int top=y+(int)Math.round(row.top-scroll);int rh=(int)Math.ceil(row.height);if(top+rh<y||top>=y+height||row.visibility<.6F)continue;
            boolean over=mouseX>=x&&mouseX<x+width-5&&mouseY>=top&&mouseY<top+rh&&mouseY>=y&&mouseY<y+height;
            float hoverValue=hover.getOrDefault(row.key,0F);hoverValue+=(over?1-hoverValue:-hoverValue)*(1-(float)Math.exp(-dt*18));hover.put(row.key,hoverValue);
            boolean selected=row.key.equals("root")?data.selectedDevice().isEmpty():!row.device.isEmpty()&&row.device.equals(data.selectedDevice())&&row.slot==data.selectedCell();
            int bg=selected?p.selected():DashboardPalette.mix(p.panel(),p.hover(),hoverValue);
            ControllerScreen.rounded(g,x+1,top,width-7,Math.max(1,rh-1),3,bg);
            if(selected)g.fill(x+1,top+3,x+3,top+rh-4,p.accent());
            for(int depth=1;depth<row.depth;depth++)g.fill(x+9+depth*11,top,x+10+depth*11,top+rh,p.border());
            int arrowX=x+7+row.depth*11,iconX=arrowX+10;
            if(row.branch)chevron(g,arrowX,top+(rowHeight-5)/2,isOpen(row.key)||!query.isBlank(),p.muted());
            int ink=DashboardPalette.mix(p.panel(),p.text(),row.visibility);
            if(!row.icon.isEmpty())g.renderItem(row.icon,iconX,top+(rowHeight-16)/2);
            else {int cy=top+rowHeight/2;if(row.slot>=0){g.fill(iconX+2,cy-5,iconX+13,cy+5,p.muted());g.fill(iconX+3,cy-4,iconX+12,cy+4,bg);}else{g.fill(iconX+2,cy-4,iconX+11,cy+4,p.muted());g.fill(iconX+2,cy-6,iconX+7,cy-4,p.muted());}}
            int textX=iconX+19;String text=row.label.getString();int max=width-(textX-x)-9;
            if(font.width(text)>max)text=font.plainSubstrByWidth(text,Math.max(0,max-6))+"…";
            g.drawString(font,text,textX,top+(rowHeight-9)/2,ink,false);
            var cell=cellPreviews.get(row.key);
            if(cell!=null&&cell.totalBytes()>0&&cell.usedBytes()>=0&&row.visibility>.95F){
                int barY=top+rowHeight-3,barWidth=Math.max(1,max);float ratio=Math.max(0,Math.min(1,cell.usedBytes()/(float)cell.totalBytes()));
                g.fill(textX,barY,textX+barWidth,barY+2,p.border());
                if(ratio>0)g.fill(textX,barY,textX+Math.max(1,Math.round(barWidth*ratio)),barY+2,ratio>=.95F?p.danger():ratio>=.8F?p.warning():p.accent());
            }
        }
        if(totalHeight>height){int thumb=Math.max(12,(int)(height*height/totalHeight));int thumbY=y+(int)((height-thumb)*scroll/(totalHeight-height));ControllerScreen.rounded(g,x+width-3,thumbY,2,thumb,1,p.muted());}
        g.disableScissor();
    }
    private static void chevron(GuiGraphics g,int x,int y,boolean down,int color){
        if(down){g.fill(x,y,x+1,y+2,color);g.fill(x+1,y+1,x+2,y+3,color);g.fill(x+2,y+2,x+3,y+4,color);g.fill(x+3,y+1,x+4,y+3,color);g.fill(x+4,y,x+5,y+2,color);}
        else{g.fill(x,y,x+2,y+1,color);g.fill(x+1,y+1,x+3,y+2,color);g.fill(x+2,y+2,x+4,y+3,color);g.fill(x+1,y+3,x+3,y+4,color);g.fill(x,y+4,x+2,y+5,color);}
    }
    boolean click(double mx,double my){
        if(mx<x||mx>=x+width||my<y||my>=y+height)return false;
        for(var row:rows){double top=y+row.top-scroll;if(row.visibility<.65F||my<top||my>=top+row.height)continue;
            if(row.branch&&mx<x+17+row.depth*11){setOpen(row.key,!isOpen(row.key));return true;}
            if(row.key.startsWith("dim:")){setOpen(row.key,!isOpen(row.key));return true;}
            if(row.key.equals("root")){select.accept("",-1);return true;}
            if(!row.device.isEmpty()){if(row.slot<0)setOpen(row.key,true);select.accept(row.device,row.slot);return true;}
        }return true;
    }
    boolean wheel(double mx,double my,double amount){if(mx<x||mx>=x+width||my<y||my>=y+height)return false;revealKey=null;scroll=Math.max(0,Math.min(Math.max(0,totalHeight-height),scroll-amount*rowHeight*2));return true;}
    List<Component> tooltip(int mx,int my){
        if(mx<x||mx>=x+width||my<y||my>=y+height)return List.of();
        for(var row:rows){double top=y+row.top-scroll;if(row.visibility<.65F||my<top||my>=top+row.height)continue;
            var result=new ArrayList<Component>();result.add(row.label);
            if(!row.device.isEmpty())snapshot.directory().stream().filter(e->e.device().id().equals(row.device)).findFirst().ifPresent(e->{
                if(row.slot<0){var d=e.device();result.add(Component.literal(d.dimension()+" · "+d.pos().getX()+", "+d.pos().getY()+", "+d.pos().getZ()));if(e.truncated())result.add(tr("directory_limited"));}
                else e.cells().stream().filter(c->c.slot()==row.slot).findFirst().ifPresent(c->result.add(tr("bytes",c.usedBytes()<0?tr("unknown"):ControllerScreen.number(c.usedBytes()),c.totalBytes()<0?tr("unknown"):ControllerScreen.number(c.totalBytes()))));
            });return result;
        }return List.of();
    }
    ControllerScreen.UiRect rect(String key,boolean chevron){for(var row:rows)if(row.key.equals(key)&&row.visibility>.9F){int top=y+(int)Math.round(row.top-scroll);if(top<y||top+rowHeight>y+height)return null;return chevron?new ControllerScreen.UiRect(x+5+row.depth*11,top,9,rowHeight):new ControllerScreen.UiRect(x+18+row.depth*11,top,Math.max(8,width-24-row.depth*11),rowHeight);}return null;}
}
