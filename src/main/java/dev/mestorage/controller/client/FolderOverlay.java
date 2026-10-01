package dev.mestorage.controller.client;

import java.util.*;
import dev.mestorage.controller.folder.FolderBook;
import dev.mestorage.controller.menu.ControllerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Small in-container dialogs. Every input remains modal so carried stacks cannot fall through. */
final class FolderOverlay {
    private enum Mode { CLOSED, CONTEXT, NAME, TARGET, DELETE, WAIT, ERROR }
    private record Action(String id,Component label,boolean enabled,Runnable run) {}
    private record Target(UUID id,String path) {}
    private final ControllerMenu menu;
    private final StorageTree tree;
    private Mode mode=Mode.CLOSED;
    private final List<Action> actions=new ArrayList<>();
    private StorageTree.Selection selection=new StorageTree.Selection(List.of(),List.of());
    private List<Target> targets=List.of();
    private int width,height,x,y,w,h,targetScroll;
    private double wheelRemainder;
    private UUID contextFolder,newParent,target;
    private boolean rename;
    private EditBox name;
    private Font font;
    private FolderBook.Edit pendingEdit;
    private Set<UUID> previousFolders=Set.of();

    FolderOverlay(ControllerMenu menu,StorageTree tree){this.menu=menu;this.tree=tree;}
    private static Component tr(String key,Object... args){return ControllerScreen.tr("folder_"+key,args);}
    boolean open(){return mode!=Mode.CLOSED;}
    void resize(Font font,int width,int height){this.font=font;this.width=width;this.height=height;layout();}
    void context(int mx,int my,StorageTree.Row row){
        selection=tree.selection();contextFolder=row==null?null:tree.folderId(row.key());
        mode=Mode.CONTEXT;actions.clear();
        var selectedFolder=selection.folders().size()==1&&selection.members().isEmpty()?tree.folder(selection.folders().get(0)):null;
        boolean ready=!menu.isFolderPending();
        actions.add(new Action(selection.empty()?"new":"group_new",tr(selection.empty()?"new":"group_new"),ready,()->{
            rename=false;newParent=selectedFolder==null?null:selectedFolder.parent();openName("");
        }));
        if(contextFolder!=null)actions.add(new Action("new_child",tr("new_child"),ready,()->{rename=false;newParent=contextFolder;selection=new StorageTree.Selection(List.of(),List.of());openName("");}));
        if(selectedFolder!=null)actions.add(new Action("rename",tr("rename"),ready,()->{rename=true;contextFolder=selectedFolder.id();openName(selectedFolder.name());}));
        if(!selection.empty()){
            actions.add(new Action("move",tr("move"),ready,this::openTargets));
            actions.add(new Action("unassign",tr("unassign"),ready,()->submit(new FolderBook.Edit(FolderBook.Op.ASSIGN,null,null,"",selection.members(),selection.folders()))));
        }
        if(selectedFolder!=null)actions.add(new Action("delete",tr("delete"),ready,()->{contextFolder=selectedFolder.id();mode=Mode.DELETE;layout();}));
        x=mx;y=my;layout();
    }
    private void layout(){
        if(font==null)return;
        w=Math.max(120,Math.min(mode==Mode.CONTEXT?184:248,width-12));
        h=mode==Mode.CONTEXT?actions.size()*18+8:mode==Mode.TARGET?Math.min(196,height-12):mode==Mode.DELETE?104:mode==Mode.ERROR?112:mode==Mode.WAIT?64:96;
        h=Math.min(h,height-8);
        if(mode!=Mode.CONTEXT){x=(width-w)/2;y=(height-h)/2;}else{x=Math.max(4,Math.min(x,width-w-4));y=Math.max(4,Math.min(y,height-h-4));}
        if(name!=null){name.setX(x+10);name.setY(y+29);name.setWidth(w-20);}
    }
    private void openName(String initial){
        mode=Mode.NAME;layout();name=new EditBox(font,x+10,y+29,w-20,16,tr("name"));
        name.setBordered(false);name.setMaxLength(FolderBook.MAX_NAME);name.setValue(initial);name.setFocused(true);name.setCursorPosition(initial.length());name.setHighlightPos(0);
    }
    private void openTargets(){
        var all=new HashMap<UUID,FolderBook.Folder>();for(var folder:menu.getFolderView().folders())all.put(folder.id(),folder);
        var result=new ArrayList<Target>();result.add(new Target(null,ControllerScreen.tr("network_root").getString()));
        for(var folder:all.values()){
            boolean excluded=false;var visited=new HashSet<UUID>();var current=folder;
            while(current!=null&&visited.add(current.id())){if(selection.folders().contains(current.id())){excluded=true;break;}current=current.parent()==null?null:all.get(current.parent());}
            if(!excluded)result.add(new Target(folder.id(),path(folder,all)));
        }
        result.subList(1,result.size()).sort(Comparator.comparing(Target::path,String.CASE_INSENSITIVE_ORDER));
        targets=List.copyOf(result);target=null;targetScroll=0;wheelRemainder=0;mode=Mode.TARGET;layout();
    }
    private String path(FolderBook.Folder folder,Map<UUID,FolderBook.Folder> all){
        var parts=new ArrayList<String>();var seen=new HashSet<UUID>();
        for(var current=folder;current!=null&&seen.add(current.id());current=current.parent()==null?null:all.get(current.parent()))parts.add(current.name());
        Collections.reverse(parts);return String.join(" / ",parts);
    }
    private int targetRows(){return Math.max(1,(h-57)/17);}
    private boolean canSubmit(){return mode!=Mode.NAME||name!=null&&!name.getValue().strip().isEmpty();}
    private void confirm(){
        if(mode==Mode.WAIT||!canSubmit()||menu.isFolderPending())return;
        switch(mode){
            case NAME -> submit(new FolderBook.Edit(rename?FolderBook.Op.RENAME:FolderBook.Op.CREATE,rename?contextFolder:null,
                    rename?null:newParent,name.getValue().strip(),rename?List.of():selection.members(),rename?List.of():selection.folders()));
            case TARGET -> submit(new FolderBook.Edit(FolderBook.Op.ASSIGN,target,null,"",selection.members(),selection.folders()));
            case DELETE -> submit(new FolderBook.Edit(FolderBook.Op.DELETE,contextFolder,null,"",List.of(),List.of()));
            default -> close();
        }
    }
    private void submit(FolderBook.Edit edit){
        previousFolders=new HashSet<>();for(var folder:menu.getFolderView().folders())previousFolders.add(folder.id());
        pendingEdit=edit;mode=Mode.WAIT;layout();menu.requestFolderEdit(edit);
    }
    void tick(){
        if(mode==Mode.NAME&&name!=null)name.tick();
        if(mode==Mode.WAIT&&!menu.isFolderPending()){
            if(!menu.getFolderError().isEmpty()){mode=Mode.ERROR;layout();return;}
            if(pendingEdit!=null&&pendingEdit.op()==FolderBook.Op.CREATE){
                var created=menu.getFolderView().folders().stream().filter(f->!previousFolders.contains(f.id())&&f.name().equals(pendingEdit.name())&&Objects.equals(f.parent(),pendingEdit.parent())).toList();
                if(created.size()==1)tree.revealFolder(created.get(0).id());
            }
            close();
        }
    }
    void close(){mode=Mode.CLOSED;name=null;actions.clear();}
    boolean click(double mx,double my,int button){
        if(!open())return false;
        if(button!=0)return true;
        if(mode==Mode.CONTEXT){for(int i=0;i<actions.size();i++)if(contains(actionRect(i),mx,my)){var action=actions.get(i);if(action.enabled)action.run.run();return true;}close();return true;}
        if(mode==Mode.WAIT)return true;
        if(mode==Mode.NAME&&name!=null&&name.mouseClicked(mx,my,button))return true;
        if(mode==Mode.TARGET)for(int i=0;i<targetRows()&&targetScroll+i<targets.size();i++)if(contains(targetRect(i),mx,my)){target=targets.get(targetScroll+i).id();return true;}
        if(contains(cancelRect(),mx,my)){close();return true;}
        if(contains(submitRect(),mx,my)){confirm();return true;}
        return true;
    }
    boolean key(int key,int scan,int modifiers){if(!open())return false;if(key==256){if(mode!=Mode.WAIT)close();return true;}if(key==257||key==335){confirm();return true;}if(mode==Mode.NAME&&name!=null)name.keyPressed(key,scan,modifiers);return true;}
    boolean character(char c,int modifiers){if(!open())return false;if(mode==Mode.NAME&&name!=null)name.charTyped(c,modifiers);return true;}
    boolean wheel(double amount){if(!open())return false;if(mode==Mode.TARGET&&Double.isFinite(amount)){wheelRemainder-=amount;int steps=(int)wheelRemainder;if(steps!=0){wheelRemainder-=steps;targetScroll=Math.max(0,Math.min(Math.max(0,targets.size()-targetRows()),targetScroll+steps));}}return true;}
    void render(GuiGraphics g,DashboardPalette palette,int mx,int my){
        if(!open())return;
        g.pose().pushPose();g.pose().translate(0,0,600);g.fill(0,0,width,height,0x66000000);
        box(g,x,y,w,h,palette);
        if(mode==Mode.CONTEXT){for(int i=0;i<actions.size();i++){var action=actions.get(i);button(g,actionRect(i),action.label,action.enabled,palette,mx,my);}g.pose().popPose();return;}
        Component title=tr(switch(mode){case NAME->rename?"rename":"new";case TARGET->"move";case DELETE->"delete";case WAIT->"waiting";case ERROR->"failed";default->"new";});
        g.drawString(font,title,x+10,y+10,palette.text(),false);
        if(mode==Mode.NAME&&name!=null){g.fill(name.getX()-2,name.getY()-2,name.getX()+name.getWidth()+2,name.getY()+16,palette.inset());name.setTextColor(palette.text());name.render(new ShadowlessTextGraphics(g),mx,my,0);}
        if(mode==Mode.DELETE)g.drawWordWrap(font,tr("delete_confirm"),x+10,y+28,w-20,palette.text());
        if(mode==Mode.ERROR)g.drawWordWrap(font,Component.translatable(menu.getFolderError()),x+10,y+28,w-20,palette.danger());
        if(mode==Mode.TARGET){
            for(int i=0;i<targetRows()&&targetScroll+i<targets.size();i++){var entry=targets.get(targetScroll+i);var rect=targetRect(i);if(Objects.equals(target,entry.id()))g.fill(rect.x(),rect.y(),rect.x()+rect.width(),rect.y()+rect.height(),palette.selected());else if(contains(rect,mx,my))g.fill(rect.x(),rect.y(),rect.x()+rect.width(),rect.y()+rect.height(),palette.hover());
                String text=entry.path();if(font.width(text)>rect.width()-5)text=font.plainSubstrByWidth(text,rect.width()-12)+"…";g.drawString(font,text,rect.x()+3,rect.y()+4,palette.text(),false);}
            if(targets.size()>targetRows()){int track=targetRows()*17,thumb=Math.max(8,track*targetRows()/targets.size()),top=y+26+(track-thumb)*targetScroll/Math.max(1,targets.size()-targetRows());g.fill(x+w-6,top,x+w-4,top+thumb,palette.muted());}
        }
        if(mode!=Mode.WAIT){button(g,cancelRect(),tr("cancel"),true,palette,mx,my);button(g,submitRect(),tr(mode==Mode.ERROR?"close":"confirm"),canSubmit(),palette,mx,my);}
        g.pose().popPose();
    }
    private void box(GuiGraphics g,int x,int y,int w,int h,DashboardPalette p){g.fill(x,y,x+w,y+h,p.border());g.fill(x+1,y+1,x+w-1,y+h-2,p.panel());g.fill(x+1,y+1,x+w-1,y+2,p.hover());g.fill(x+1,y+h-3,x+w-1,y+h-1,p.inset());}
    private void button(GuiGraphics g,ControllerScreen.UiRect r,Component label,boolean enabled,DashboardPalette p,int mx,int my){g.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),enabled&&contains(r,mx,my)?p.selected():p.inset());g.fill(r.x(),r.y()+r.height()-1,r.x()+r.width(),r.y()+r.height(),p.border());String text=label.getString();if(font.width(text)>r.width()-8)text=font.plainSubstrByWidth(text,r.width()-14)+"…";g.drawString(font,text,r.x()+4,r.y()+(r.height()-8)/2,enabled?p.text():p.muted(),false);}
    private static boolean contains(ControllerScreen.UiRect rect,double x,double y){return rect!=null&&x>=rect.x()&&x<rect.x()+rect.width()&&y>=rect.y()&&y<rect.y()+rect.height();}
    private ControllerScreen.UiRect actionRect(int index){return new ControllerScreen.UiRect(x+4,y+4+index*18,w-8,17);}
    private ControllerScreen.UiRect targetRect(int row){return new ControllerScreen.UiRect(x+8,y+26+row*17,w-18,17);}
    ControllerScreen.UiRect submitRect(){return open()&&mode!=Mode.CONTEXT&&mode!=Mode.WAIT?new ControllerScreen.UiRect(x+w/2+3,y+h-24,w/2-11,17):null;}
    ControllerScreen.UiRect cancelRect(){return open()&&mode!=Mode.CONTEXT&&mode!=Mode.WAIT?new ControllerScreen.UiRect(x+8,y+h-24,w/2-11,17):null;}
    ControllerScreen.UiRect nameRect(){return mode==Mode.NAME&&name!=null?new ControllerScreen.UiRect(name.getX(),name.getY(),name.getWidth(),name.getHeight()):null;}
    ControllerScreen.UiRect actionRect(String id){if(mode!=Mode.CONTEXT)return null;for(int i=0;i<actions.size();i++)if(actions.get(i).id.equals(id))return actionRect(i);return null;}
    ControllerScreen.UiRect targetRect(UUID id){if(mode!=Mode.TARGET)return null;for(int i=0;i<targetRows()&&targetScroll+i<targets.size();i++)if(Objects.equals(id,targets.get(targetScroll+i).id()))return targetRect(i);return null;}
    void revealTarget(UUID id){if(mode!=Mode.TARGET)return;for(int i=0;i<targets.size();i++)if(Objects.equals(id,targets.get(i).id())){targetScroll=Math.max(0,Math.min(i,targets.size()-targetRows()));return;}}

    /** Changes only rendering; EditBox keeps its own cursor, selection and horizontal scrolling. */
    private static final class ShadowlessTextGraphics extends GuiGraphics {
        private final GuiGraphics delegate;
        ShadowlessTextGraphics(GuiGraphics delegate){super(Minecraft.getInstance(),delegate.bufferSource());this.delegate=delegate;}
        // Vanilla's shadowed draw returns the final X plus one. Keep that advance
        // because EditBox compensates for it when positioning its caret and suffix.
        @Override public int drawString(Font font,FormattedCharSequence text,int x,int y,int color){return delegate.drawString(font,text,x,y,color,false)+1;}
        @Override public int drawString(Font font,String text,int x,int y,int color){return delegate.drawString(font,text,x,y,color,false)+1;}
        @Override public int drawString(Font font,Component text,int x,int y,int color){return delegate.drawString(font,text,x,y,color,false)+1;}
        // Delegate rather than using this proxy's fresh pose: the modal is at Z=600.
        @Override public void fill(int x1,int y1,int x2,int y2,int color){delegate.fill(x1,y1,x2,y2,color);}
        @Override public void fill(RenderType type,int x1,int y1,int x2,int y2,int color){delegate.fill(type,x1,y1,x2,y2,color);}
    }
}
