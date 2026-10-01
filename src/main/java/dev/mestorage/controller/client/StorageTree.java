package dev.mestorage.controller.client;

import java.util.*;
import java.util.function.BiConsumer;
import dev.mestorage.controller.network.Snapshot;
import dev.mestorage.controller.folder.FolderBook;
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
    private FolderBook.View folders=new FolderBook.View(0,List.of());
    private final LinkedHashSet<String> selection=new LinkedHashSet<>();
    private String selectionAnchor;
    private Map<String,Node> nodes=Map.of();
    private Map<String,String> nodeParents=Map.of();
    private Map<String,Set<Integer>> assignedSlots=Map.of();
    private Map<String,Map<Integer,Snapshot.CellPreview>> deviceCells=Map.of();
    private Node structure;
    private boolean structureDirty=true;
    private record Node(String key,String device,int slot,Component label,ItemStack icon,boolean branch,List<Node> children) {}
    record Selection(List<FolderBook.MemberRef> members,List<UUID> folders) {
        boolean empty(){return members.isEmpty()&&folders.isEmpty();}
    }
    private String query="",revealKey;
    private int x,y,width,height,rowHeight=20;
    private double scroll,totalHeight;
    private boolean layoutDirty=true;
    private final BiConsumer<String,Integer> select;
    StorageTree(BiConsumer<String,Integer> select){this.select=select;}
    Map<String,Boolean> expansionState(){return Map.copyOf(opened);}
    void restoreExpansionState(Map<String,Boolean> state){
        opened.clear();opened.putAll(state);motions.clear();revealKey=null;scroll=0;layoutDirty=true;
        // Do not filter against the current directory: its first generation may still be in flight.
    }
    void setFolders(FolderBook.View value){if(folders!=value){folders=value;layoutDirty=true;structureDirty=true;}}
    FolderBook.Folder folder(UUID id){return folders.folders().stream().filter(f->f.id().equals(id)).findFirst().orElse(null);}
    UUID folderId(String key){try{return key!=null&&key.startsWith("folder:")?UUID.fromString(key.substring(7)):null;}catch(IllegalArgumentException ignored){return null;}}
    String folderPath(UUID id){var parts=new ArrayList<String>();var seen=new HashSet<UUID>();for(var f=folder(id);f!=null&&seen.add(f.id());f=f.parent()==null?null:folder(f.parent()))parts.add(f.name());Collections.reverse(parts);return String.join(" / ",parts);}
    Selection selection(){
        var members=new ArrayList<FolderBook.MemberRef>();var selectedFolders=new ArrayList<UUID>();
        for(String key:selection){var node=nodes.get(key);if(node==null)continue;boolean covered=false;for(String parent=nodeParents.get(key);parent!=null;parent=nodeParents.get(parent))if(selection.contains(parent)){covered=true;break;}if(covered)continue;
            var id=folderId(key);if(id!=null)selectedFolders.add(id);else if(!node.device.isEmpty())members.add(new FolderBook.MemberRef(node.device,node.slot));}
        // Editing ownership differs from aggregating contents: a separately assigned
        // slot must move too even when its whole device is selected elsewhere.
        return new Selection(List.copyOf(members),List.copyOf(selectedFolders));
    }
    Set<String> selectedKeys(){return Set.copyOf(selection);}
    void revealFolder(UUID id){setOpen("root",true);var seen=new HashSet<UUID>();var f=folder(id);while(f!=null&&seen.add(f.id())){if(f.parent()!=null)setOpen("folder:"+f.parent(),true);f=f.parent()==null?null:folder(f.parent());}revealKey="folder:"+id;}
    private static Component tr(String k,Object... a){return Component.translatable("gui.me_storage_controller."+k,a);}
    private boolean defaultOpen(String key){return key.equals("root")||key.startsWith("dim:");}
    boolean isOpen(String key){return opened.getOrDefault(key,defaultOpen(key));}
    private float amount(String key){return query.isBlank()?motions.containsKey(key)?motions.get(key).value():isOpen(key)?1:0:1;}
    private void setOpen(String key,boolean value){
        if(isOpen(key)==value)return;
        float old=amount(key);opened.put(key,value);motions.put(key,new Motion(old,value?1:0,System.nanoTime()));layoutDirty=true;
    }
    void expandDevice(String id){setOpen("root",true);setOpen("dev:"+id,true);
        revealMemberAncestors(id,-1);snapshot.directory().stream().filter(d->d.device().id().equals(id)).findFirst().ifPresent(d->setOpen("dim:"+d.device().dimension(),true));}
    private void revealMemberAncestors(String device,int slot){for(var f:folders.folders())if(f.members().stream().anyMatch(m->m.deviceId().equals(device)&&(m.cell()<0||m.cell()==slot))){revealFolder(f.id());setOpen("folder:"+f.id(),true);}}
    void revealCell(String id,int slot){expandDevice(id);revealMemberAncestors(id,slot);revealKey="cell:"+id+":"+slot;}
    void revealRoot(){setOpen("root",true);revealKey="root";scroll=0;}
    void revealDevice(String id){setOpen("root",true);revealMemberAncestors(id,-1);snapshot.directory().stream().filter(d->d.device().id().equals(id)).findFirst().ifPresent(d->setOpen("dim:"+d.device().dimension(),true));revealKey="dev:"+id;}
    double scrollOffset(){return scroll;}
    void setQuery(String value){String normalized=value.toLowerCase(Locale.ROOT).trim();if(!query.equals(normalized)){query=normalized;scroll=0;layoutDirty=true;}}
    private boolean matches(String value){return value.toLowerCase(Locale.ROOT).contains(query);}
    private Component dimension(String id){return switch(id){case "minecraft:overworld"->tr("dimension_overworld");case "minecraft:the_nether"->tr("dimension_nether");case "minecraft:the_end"->tr("dimension_end");default->Component.literal(id);};}
    private Component cellLabel(Snapshot.CellPreview cell){return Component.literal((cell.slot()+1)+"  ").append(cell.icon().isEmpty()?tr("empty_cell"):cell.icon().getHoverName());}
    private Node cellNode(String device,int slot,Snapshot.DirectoryEntry entry,boolean withDevice,Map<String,Snapshot.CellPreview> previews){
        var cell=deviceCells.getOrDefault(device,Map.of()).get(slot);
        String key="cell:"+device+":"+slot;
        Component label=cell==null?tr("folder_unavailable_cell",slot+1):cellLabel(cell);
        if(withDevice)label=Component.literal(entry==null?device:entry.device().name().getString()).append(" · ").append(label);
        if(cell!=null)previews.put(key,cell);
        return new Node(key,device,slot,label,cell==null?ItemStack.EMPTY:cell.icon(),false,List.of());
    }
    private Node deviceNode(String device,Snapshot.DirectoryEntry entry,Set<Integer> omit,Map<String,Snapshot.CellPreview> previews){
        var children=new ArrayList<Node>();
        if(entry!=null)for(var cell:entry.cells())if(!omit.contains(cell.slot()))children.add(cellNode(device,cell.slot(),entry,false,previews));
        return new Node("dev:"+device,device,-1,entry==null?tr("folder_unavailable_device",device):entry.device().name(),
                entry==null?ItemStack.EMPTY:entry.device().icon(),!children.isEmpty(),children);
    }
    private Node folderNode(FolderBook.Folder folder,Map<String,Snapshot.DirectoryEntry> devices,Map<UUID,List<FolderBook.Folder>> children,
                            Set<UUID> visiting,Map<String,Snapshot.CellPreview> previews){
        if(!visiting.add(folder.id()))return null;
        var result=new ArrayList<Node>();
        for(var child:children.getOrDefault(folder.id(),List.of())){var node=folderNode(child,devices,children,visiting,previews);if(node!=null)result.add(node);}
        for(var member:folder.members())result.add(member.cell()<0?deviceNode(member.deviceId(),devices.get(member.deviceId()),assignedSlots.getOrDefault(member.deviceId(),Set.of()),previews)
                :cellNode(member.deviceId(),member.cell(),devices.get(member.deviceId()),true,previews));
        return new Node("folder:"+folder.id(),"folder:"+folder.id(),-1,Component.literal(folder.name()),ItemStack.EMPTY,true,result);
    }
    private Node directory(Map<String,Snapshot.CellPreview> previews){
        var devices=new LinkedHashMap<String,Snapshot.DirectoryEntry>();for(var entry:snapshot.directory())devices.put(entry.device().id(),entry);
        var cellIndex=new HashMap<String,Map<Integer,Snapshot.CellPreview>>();for(var entry:devices.values()){var index=new HashMap<Integer,Snapshot.CellPreview>();for(var cell:entry.cells())index.put(cell.slot(),cell);cellIndex.put(entry.device().id(),index);}deviceCells=cellIndex;
        var whole=new HashSet<String>();var assigned=new HashMap<String,Set<Integer>>();
        var folderChildren=new HashMap<UUID,List<FolderBook.Folder>>();var ids=new HashSet<UUID>();
        for(var folder:folders.folders())ids.add(folder.id());
        var roots=new ArrayList<FolderBook.Folder>();
        for(var folder:folders.folders()){
            if(folder.parent()==null||!ids.contains(folder.parent()))roots.add(folder);else folderChildren.computeIfAbsent(folder.parent(),k->new ArrayList<>()).add(folder);
            for(var member:folder.members())if(member.cell()<0)whole.add(member.deviceId());else assigned.computeIfAbsent(member.deviceId(),k->new HashSet<>()).add(member.cell());
        }
        assignedSlots=assigned;
        var result=new ArrayList<Node>();var visited=new HashSet<UUID>();
        for(var folder:roots){var node=folderNode(folder,devices,folderChildren,visited,previews);if(node!=null)result.add(node);}
        var dimensions=new TreeMap<String,List<Node>>();
        for(var entry:devices.values()){
            var d=entry.device();var omit=assigned.getOrDefault(d.id(),Set.of());
            if(whole.contains(d.id())||entry.cellSlots()>0&&omit.size()>=entry.cellSlots())continue;
            dimensions.computeIfAbsent(d.dimension().toString(),k->new ArrayList<>()).add(deviceNode(d.id(),entry,omit,previews));
        }
        for(var dimension:dimensions.entrySet())result.add(new Node("dim:"+dimension.getKey(),"",-1,dimension(dimension.getKey()),ItemStack.EMPTY,true,dimension.getValue()));
        var root=new Node("root","",-1,tr("network_root"),ItemStack.EMPTY,true,result);
        var indexed=new HashMap<String,Node>();var parents=new HashMap<String,String>();index(root,null,indexed,parents);nodes=indexed;nodeParents=parents;selection.removeIf(key->!nodes.containsKey(key));
        return root;
    }
    private void index(Node node,String parent,Map<String,Node> result,Map<String,String> parents){result.put(node.key,node);if(parent!=null)parents.put(node.key,parent);for(var child:node.children)index(child,node.key,result,parents);}
    private Node filtered(Node node,boolean ancestorMatch){
        boolean match=ancestorMatch||matches(node.label.getString()+" "+node.key);var children=new ArrayList<Node>();
        for(var child:node.children){var filtered=filtered(child,match&&!node.key.equals("root"));if(filtered!=null)children.add(filtered);}
        return node.key.equals("root")||match||!children.isEmpty()?new Node(node.key,node.device,node.slot,node.label,node.icon,node.branch,children):null;
    }
    private double flatten(Node node,String parent,int depth,float visibility,double top,List<Row> result){
        if(visibility<=.001F)return top;
        result.add(new Row(node.key,parent,node.device,node.slot,depth,node.label,node.icon,node.branch,visibility,top,rowHeight*visibility));
        top+=rowHeight*visibility;
        float childVisibility=visibility*amount(node.key);
        for(var child:node.children)top=flatten(child,node.key,depth+1,childVisibility,top,result);
        return top;
    }
    private void layout(){
        boolean moving=motions.values().stream().anyMatch(m->System.nanoTime()-m.started()<160_000_000L);
        if(!layoutDirty&&!moving&&revealKey==null)return;
        var built=new ArrayList<Row>();
        if(structureDirty||structure==null){var previews=new HashMap<String,Snapshot.CellPreview>();structure=directory(previews);cellPreviews=previews;structureDirty=false;}
        var root=structure;if(!query.isBlank())root=filtered(root,false);
        double top=flatten(root,"",0,1,0,built);
        rows=built;totalHeight=top;
        // An earlier expanded branch can keep pushing the requested row down. Track it until
        // every expansion has settled, instead of clearing the reveal on its first visible frame.
        if(revealKey!=null){for(var row:rows)if(row.key.equals(revealKey)&&row.visibility>.95F){if(row.top<scroll)scroll=row.top;else if(row.top+row.height>scroll+height)scroll=row.top+row.height-height;if(!moving)revealKey=null;break;}}
        if(!moving&&revealKey!=null&&rows.stream().noneMatch(row->row.key.equals(revealKey)))revealKey=null;
        scroll=Math.max(0,Math.min(scroll,Math.max(0,totalHeight-height)));
        layoutDirty=moving; // Build the settled final geometry once after an animation ends.
    }
    void render(GuiGraphics g,int x,int y,int width,int height,boolean compact,Snapshot data,DashboardPalette p,int mouseX,int mouseY,float dt){
        int nextRowHeight=compact?13:18;
        if(snapshot.directory()!=data.directory()){layoutDirty=true;structureDirty=true;}
        if(this.width!=width||this.height!=height||rowHeight!=nextRowHeight)layoutDirty=true;
        this.x=x;this.y=y;this.width=width;this.height=height;this.snapshot=data;rowHeight=nextRowHeight;layout();
        var font=Minecraft.getInstance().font;
        g.enableScissor(x,y,x+width,y+height);
        for(int rowIndex=firstVisible();rowIndex<rows.size();rowIndex++){
            var row=rows.get(rowIndex);
            int top=y+(int)Math.round(row.top-scroll);int rh=(int)Math.ceil(row.height);if(top>=y+height)break;if(top+rh<y||row.visibility<.6F)continue;
            boolean over=mouseX>=x&&mouseX<x+width-5&&mouseY>=top&&mouseY<top+rh&&mouseY>=y&&mouseY<y+height;
            float hoverValue=hover.getOrDefault(row.key,0F);hoverValue+=(over?1-hoverValue:-hoverValue)*(1-(float)Math.exp(-dt*18));hover.put(row.key,hoverValue);
            boolean selected=selection.contains(row.key)||(row.key.equals("root")?data.selectedDevice().isEmpty():!row.device.isEmpty()&&row.device.equals(data.selectedDevice())&&row.slot==data.selectedCell());
            int bg=selected?p.selected():DashboardPalette.mix(p.panel(),p.hover(),hoverValue);
            ControllerScreen.rounded(g,x+1,top,width-7,Math.max(1,rh-1),3,bg);
            if(selected)g.fill(x+1,top+3,x+3,top+rh-4,p.accent());
            for(int depth=1;depth<Math.min(row.depth,6);depth++)g.fill(x+9+depth*6,top,x+10+depth*6,top+rh,p.border());
            int arrowX=x+7+Math.min(row.depth,6)*6,iconX=arrowX+8;
            if(row.branch)chevron(g,arrowX,top+(rowHeight-5)/2,isOpen(row.key)||!query.isBlank(),p.muted());
            int ink=DashboardPalette.mix(p.panel(),p.text(),row.visibility);
            if(!row.icon.isEmpty()){g.pose().pushPose();g.pose().translate(iconX,top+(rowHeight-10)/2,0);g.pose().scale(.625F,.625F,1);g.renderItem(row.icon,0,0);g.pose().popPose();}
            else {int cy=top+rowHeight/2;if(row.slot>=0){g.fill(iconX+2,cy-5,iconX+13,cy+5,p.muted());g.fill(iconX+3,cy-4,iconX+12,cy+4,bg);}else{g.fill(iconX+2,cy-4,iconX+11,cy+4,p.muted());g.fill(iconX+2,cy-6,iconX+7,cy-4,p.muted());}}
            int textX=iconX+13;String text=row.label.getString();int max=width-(textX-x)-9;
            if(font.width(text)>max)text=font.plainSubstrByWidth(text,Math.max(0,max-6))+"…";
            g.drawString(font,text,textX,top+(rowHeight-9)/2,ink,false);
            var cell=cellPreviews.get(row.key);
            if(cell!=null&&cell.totalBytes()>0&&cell.usedBytes()>=0&&row.visibility>.95F){
                int barY=top+rowHeight-2,barWidth=Math.max(1,max);float ratio=Math.max(0,Math.min(1,cell.usedBytes()/(float)cell.totalBytes()));
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
    /** Rows stay complete, but rendering/hit testing starts at the visible viewport. */
    private int firstVisible(){
        int low=0,high=rows.size();
        while(low<high){int middle=(low+high)>>>1;var row=rows.get(middle);if(row.top+row.height<scroll)low=middle+1;else high=middle;}
        return low;
    }
    private Row at(double mx,double my){if(mx<x||mx>=x+width||my<y||my>=y+height)return null;
        for(int index=firstVisible();index<rows.size();index++){var row=rows.get(index);double top=y+row.top-scroll;if(top>=y+height)break;if(row.visibility>.65F&&my>=top&&my<top+row.height)return row;}return null;}
    Row contextAt(double mx,double my){var row=at(mx,my);if(row==null||row.device.isEmpty()){selection.clear();return row;}
        if(!selection.contains(row.key)){selection.clear();selection.add(row.key);selectionAnchor=row.key;}return row;}
    boolean click(double mx,double my){return click(mx,my,false,false);}
    boolean click(double mx,double my,boolean control,boolean shift){
        if(mx<x||mx>=x+width||my<y||my>=y+height)return false;
        var row=at(mx,my);if(row==null){if(!control&&!shift)selection.clear();return true;}
        if(row.branch&&mx<x+17+Math.min(row.depth,6)*6){setOpen(row.key,!isOpen(row.key));return true;}
        if(row.key.startsWith("dim:")){setOpen(row.key,!isOpen(row.key));return true;}
        if(row.key.equals("root")){selection.clear();select.accept("",-1);return true;}
        if(row.device.isEmpty())return true;
        if(shift&&selectionAnchor!=null){
            int start=-1,end=rows.indexOf(row);for(int i=0;i<rows.size();i++)if(rows.get(i).key.equals(selectionAnchor)){start=i;break;}
            if(!control)selection.clear();if(start>=0)for(int i=Math.min(start,end);i<=Math.max(start,end);i++){var selected=rows.get(i);if(!selected.device.isEmpty()&&selected.visibility>.95F)selection.add(selected.key);}else selection.add(row.key);
            return true;
        }
        if(control){if(!selection.add(row.key))selection.remove(row.key);selectionAnchor=row.key;return true;}
        selection.clear();selection.add(row.key);selectionAnchor=row.key;
        if(row.slot<0&&!row.key.startsWith("folder:"))setOpen(row.key,true);
        select.accept(row.device,row.slot);return true;
    }
    boolean wheel(double mx,double my,double amount){if(mx<x||mx>=x+width||my<y||my>=y+height)return false;revealKey=null;scroll=Math.max(0,Math.min(Math.max(0,totalHeight-height),scroll-amount*rowHeight*2));return true;}
    List<Component> tooltip(int mx,int my){
        if(mx<x||mx>=x+width||my<y||my>=y+height)return List.of();
        for(int index=firstVisible();index<rows.size();index++){var row=rows.get(index);double top=y+row.top-scroll;if(top>=y+height)break;if(row.visibility<.65F||my<top||my>=top+row.height)continue;
            var result=new ArrayList<Component>();result.add(row.label);
            var folderId=folderId(row.key);if(folderId!=null){String path=folderPath(folderId);if(!path.equals(row.label.getString()))result.add(Component.literal(path));return result;}
            if(!row.device.isEmpty())snapshot.directory().stream().filter(e->e.device().id().equals(row.device)).findFirst().ifPresent(e->{
                if(row.slot<0){var d=e.device();result.add(Component.literal(d.dimension()+" · "+d.pos().getX()+", "+d.pos().getY()+", "+d.pos().getZ()));}
                else {var d=e.device();result.add(Component.literal(d.dimension()+" · "+d.pos().getX()+", "+d.pos().getY()+", "+d.pos().getZ()));e.cells().stream().filter(c->c.slot()==row.slot).findFirst().ifPresent(c->result.add(tr("bytes",ControllerScreen.number(c.usedBytes()),ControllerScreen.number(c.totalBytes()))));}
            });return result;
        }return List.of();
    }
    ControllerScreen.UiRect rect(String key,boolean chevron){for(var row:rows)if(row.key.equals(key)&&row.visibility>.9F){int top=y+(int)Math.round(row.top-scroll);int indent=Math.min(row.depth,6)*6;if(top<y||top+rowHeight>y+height)return null;return chevron?new ControllerScreen.UiRect(x+5+indent,top,9,rowHeight):new ControllerScreen.UiRect(x+18+indent,top,Math.max(8,width-24-indent),rowHeight);}return null;}
}
