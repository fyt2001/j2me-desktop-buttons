import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import org.microemu.MIDletBridge;
import org.microemu.app.Headless;
import org.microemu.device.DeviceFactory;
import org.microemu.device.j2se.J2SEDeviceDisplay;
import org.microemu.device.j2se.J2SEMutableImage;

/** Desktop shell: compatibility runtime stays inside the application. */
public final class DesktopGame {
    static final Color BG=new Color(15,20,29), PANEL=new Color(24,32,44), TEXT=new Color(230,237,247), ACCENT=new Color(67,166,220);
    final JPanel root=new JPanel(new BorderLayout(0,12));
    final GameScreen screen=new GameScreen();
    final Map<Integer,Long> held=new HashMap<Integer,Long>();
    final Map<Integer,Long> releases=new HashMap<Integer,Long>();
    final javax.swing.Timer renderTimer=new javax.swing.Timer(33,new ActionListener(){public void actionPerformed(ActionEvent e){screen.repaint();}});
    JFrame window;
    Path home;

    DesktopGame(Path home) {
        this.home=home;
        root.setBackground(BG);root.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JLabel title=new JLabel("J2ME 桌面按钮");title.setForeground(TEXT);title.setFont(new Font("Microsoft YaHei",Font.BOLD,22));
        root.add(title,BorderLayout.NORTH);root.add(screen,BorderLayout.CENTER);
        JPanel controls=new JPanel(new BorderLayout(8,8));controls.setOpaque(false);
        JPanel soft=new JPanel(new GridLayout(1,2,12,0));soft.setOpaque(false);
        soft.add(button("左软键","F1",-6));soft.add(button("右软键","F2 / Esc",-7));controls.add(soft,BorderLayout.NORTH);
        JPanel pad=new JPanel(new GridLayout(4,3,8,8));pad.setOpaque(false);
        pad.add(button("1","键盘1 / 小键盘1",49));pad.add(button("2 ↑","方向↑ / 2",50));pad.add(button("3","键盘3 / 小键盘3",51));
        pad.add(button("4 ←","方向← / 4",52));pad.add(button("5 确定","回车 / 空格 / 5",53));pad.add(button("6 →","方向→ / 6",54));
        pad.add(button("7","键盘7 / 小键盘7",55));pad.add(button("8 ↓","方向↓ / 8",56));pad.add(button("9","键盘9 / 小键盘9",57));
        pad.add(button("*","Shift+8 / 小键盘*",42));pad.add(button("0","键盘0 / 小键盘0",48));pad.add(button("#","Shift+3 / #",35));controls.add(pad,BorderLayout.CENTER);
        JLabel hint=new JLabel("数字键与小键盘同号；长按方向键可连续移动",SwingConstants.CENTER);
        hint.setForeground(new Color(144,164,185));hint.setFont(new Font("Microsoft YaHei",Font.PLAIN,12));controls.add(hint,BorderLayout.SOUTH);
        root.add(controls,BorderLayout.SOUTH);
        bindKeyboard();renderTimer.start();
    }
    JButton button(String text,String shortcut,final int code) {
        final JButton b=new JButton("<html><center><b>"+text+"</b><br><font color='#9ab2ca' size='2'>"+shortcut+"</font></center></html>");
        b.setFont(new Font("Microsoft YaHei",Font.BOLD,17));
        b.setForeground(TEXT);b.setBackground(PANEL);b.setFocusPainted(false);b.setFocusable(false);b.setOpaque(true);
        b.setPreferredSize(new Dimension(90,52));b.setBorder(BorderFactory.createLineBorder(new Color(45,64,84)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){begin(code);}
            public void mouseReleased(MouseEvent e){end(code);}
            public void mouseExited(MouseEvent e){end(code);}
        });
        return b;
    }
    void bindKeyboard() {
        int[][] pairs={{KeyEvent.VK_UP,50},{KeyEvent.VK_DOWN,56},{KeyEvent.VK_LEFT,52},{KeyEvent.VK_RIGHT,54},{KeyEvent.VK_ENTER,53},{KeyEvent.VK_SPACE,53},{KeyEvent.VK_ESCAPE,-7},{KeyEvent.VK_F1,-6},{KeyEvent.VK_F2,-7},
            {KeyEvent.VK_0,48},{KeyEvent.VK_1,49},{KeyEvent.VK_2,50},{KeyEvent.VK_3,51},{KeyEvent.VK_4,52},{KeyEvent.VK_5,53},{KeyEvent.VK_6,54},{KeyEvent.VK_7,55},{KeyEvent.VK_8,56},{KeyEvent.VK_9,57},
            {KeyEvent.VK_NUMPAD0,48},{KeyEvent.VK_NUMPAD1,49},{KeyEvent.VK_NUMPAD2,50},{KeyEvent.VK_NUMPAD3,51},{KeyEvent.VK_NUMPAD4,52},{KeyEvent.VK_NUMPAD5,53},{KeyEvent.VK_NUMPAD6,54},{KeyEvent.VK_NUMPAD7,55},{KeyEvent.VK_NUMPAD8,56},{KeyEvent.VK_NUMPAD9,57},{KeyEvent.VK_MULTIPLY,42},{KeyEvent.VK_NUMBER_SIGN,35}};
        for(int[] pair:pairs)bindKey(pair[0],0,pair[1]);
        bindKey(KeyEvent.VK_8,InputEvent.SHIFT_DOWN_MASK,42);
        bindKey(KeyEvent.VK_3,InputEvent.SHIFT_DOWN_MASK,35);
    }
    void bindKey(int key,int modifiers,final int code) {
        String id="key"+key+"-"+modifiers;
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,modifiers,false),id+"down");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key,modifiers,true),id+"up");
        root.getActionMap().put(id+"down",new AbstractAction(){public void actionPerformed(ActionEvent e){begin(code);}});
        root.getActionMap().put(id+"up",new AbstractAction(){public void actionPerformed(ActionEvent e){end(code);}});
    }
    static Displayable current() {
        MIDlet m=MIDletBridge.getCurrentMIDlet();return m==null?null:Display.getDisplay(m).getCurrent();
    }
    void send(int code,boolean down) {
        try {
            Displayable d=current();if(d==null)return;
            Method method=d.getClass().getDeclaredMethod(down?"keyPressed":"keyReleased",Integer.TYPE);method.setAccessible(true);method.invoke(d,Integer.valueOf(code));
        } catch(Exception e){e.printStackTrace();}
    }
    void tap(final int code){
        send(code,true);
        final long stamp=System.nanoTime();releases.put(Integer.valueOf(code),Long.valueOf(stamp));
        javax.swing.Timer release=new javax.swing.Timer(100,new ActionListener(){public void actionPerformed(ActionEvent e){
            Long latest=releases.get(Integer.valueOf(code));
            if(latest!=null&&latest.longValue()==stamp){send(code,false);releases.remove(Integer.valueOf(code));}
        }});
        release.setRepeats(false);release.start();
    }
    void begin(int code){
        if(held.containsKey(Integer.valueOf(code)))return;
        if(code==50||code==52||code==54||code==56){
            held.put(Integer.valueOf(code),Long.valueOf(System.currentTimeMillis()));send(code,true);
        } else tap(code);
    }
    void end(final int code){
        Long started=held.remove(Integer.valueOf(code));if(started==null)return;
        int remaining=Math.max(0,100-(int)(System.currentTimeMillis()-started.longValue()));
        if(remaining==0){send(code,false);return;}
        javax.swing.Timer release=new javax.swing.Timer(remaining,new ActionListener(){public void actionPerformed(ActionEvent e){
            if(!held.containsKey(Integer.valueOf(code)))send(code,false);
        }});
        release.setRepeats(false);release.start();
    }
    void releaseDirections(){for(Integer code:new ArrayList<Integer>(held.keySet()))end(code.intValue());}
    void showWindow() {
        window=new JFrame("J2ME 桌面按钮");window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);window.setContentPane(root);
        window.setSize(520,970);window.setMinimumSize(new Dimension(360,730));window.setLocationRelativeTo(null);
        Dimension desktop=Toolkit.getDefaultToolkit().getScreenSize();window.setSize(Math.min(520,desktop.width-40),Math.min(970,desktop.height-70));
        window.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){shutdown();}});
        window.addWindowFocusListener(new WindowAdapter(){public void windowLostFocus(WindowEvent e){releaseDirections();}});
        window.setVisible(true);
    }
    void shutdown() {
        releaseDirections();renderTimer.stop();
        try {MIDlet m=MIDletBridge.getCurrentMIDlet();if(m!=null){Method destroy=m.getClass().getDeclaredMethod("destroyApp",Boolean.TYPE);destroy.setAccessible(true);destroy.invoke(m,true);}}catch(Exception e){e.printStackTrace();}
        System.exit(0);
    }
    final class GameScreen extends JPanel {
        Rectangle imageBounds=new Rectangle();
        GameScreen() {setBackground(Color.BLACK);setPreferredSize(new Dimension(480,640));}
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            try {
                if(DeviceFactory.getDevice()==null)return;
                J2SEDeviceDisplay device=(J2SEDeviceDisplay)DeviceFactory.getDevice().getDeviceDisplay();
                J2SEMutableImage frame=(J2SEMutableImage)device.getDisplayImage();if(frame==null)return;
                double scale=Math.min((double)getWidth()/frame.getWidth(),(double)getHeight()/frame.getHeight());
                int w=(int)Math.round(frame.getWidth()*scale),h=(int)Math.round(frame.getHeight()*scale);
                imageBounds.setBounds((getWidth()-w)/2,(getHeight()-h)/2,w,h);
                Graphics2D g=(Graphics2D)graphics;g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(frame.getImage(),imageBounds.x,imageBounds.y,w,h,null);
            } catch(Exception e){e.printStackTrace();}
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: DesktopGame <project-directory> <owned-game.jar>");
        final Path home=Paths.get(args[0]).toAbsolutePath();final Path game=Paths.get(args[1]).toAbsolutePath();
        if(!Files.isRegularFile(game))throw new FileNotFoundException(game.toString());
        Files.createDirectories(home.resolve("data"));Files.createDirectories(home.resolve("logs"));
        System.setProperty("user.home",home.resolve("data").toString());
        PrintStream log=new PrintStream(new FileOutputStream(home.resolve("logs/desktop.log").toFile(),true),true,"UTF-8");System.setOut(log);System.setErr(log);
        final String cp=home.resolve("lib/microemulator-2.0.4.jar")+File.pathSeparator+home.resolve("lib/microemu-nokiaui-2.0.4.jar")+File.pathSeparator+home.resolve("lib/microemu-jsr-120-2.0.4.jar");
        Headless.main(new String[]{"--rms","file","--appclassloader","relaxed","--classpath",cp,"--resizableDevice","240","320",game.toString()});
        final DesktopGame[] app=new DesktopGame[1];SwingUtilities.invokeAndWait(new Runnable(){public void run(){app[0]=new DesktopGame(home);}});
        SwingUtilities.invokeLater(new Runnable(){public void run(){app[0].showWindow();}});
    }
}

