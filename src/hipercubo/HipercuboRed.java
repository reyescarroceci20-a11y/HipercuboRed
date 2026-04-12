package hipercubo;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Cosos para acordarnos 
 * Cada nodo se identifica con 4 bits: b3 b2 b1 b0
 *   b3 = salto de conexión (cross-link entre cubos)
 *   b2 = dimensión vertical
 *   b1 = dimensión diagonal
 *   b0 = dimensión horizontal
 *
 * Tabla de verdad para seleccionar dimensión de ruteo:
 *   XOR entre bits del nodo actual y destino determina
 *   qué bit (dimensión) debe cambiarse para acercarse al destino.
 *   Se elige una de las dimensiones necesarias al azar.
 */
public class HipercuboRed extends JFrame {

    // Colores
    static final Color BG          = new Color(10, 12, 20);
    //static final Color CUBE_BG     = new Color(18, 22, 38);
    static final Color NODE_IDLE   = new Color(55, 80, 140);
    //static final Color NODE_ACTIVE = new Color(0, 220, 180);
    static final Color NODOEMISOR    = new Color(255, 200, 0);
    static final Color NODODESTINO    = new Color(255, 80, 80);
    static final Color NODORUTA   = new Color(57, 255, 20);
    //static final Color EDGE_IDLE   = new Color(57, 55, 100);
    static final Color ARISTARUTA   = new Color(0, 230, 160);
    static final Color ARISTASALTO  = new Color(180, 80, 255);   // aristas entre cubos
    static final Color TEXT_MAIN   = new Color(200, 215, 255);
    static final Color TEXT_DIM    = new Color(90, 110, 160);
    static final Color ACCENT      = new Color(0, 200, 160);
    static final Color PANEL_BG    = new Color(14, 18, 30);

    // Estado
    int source = -1, dest = -1;
    List<Integer> path = new ArrayList<>();          // nodos globales (0-15)
    List<int[]>   pathEdges = new ArrayList<>();     // pares [u,v] globales
    String routeLog = "";

    CubePanel cubePanel;
    JLabel    statusLabel;
    //JTextArea logArea;
    JComboBox<String> srcBox, dstBox;

    // Coordenadas 2D de los 8 nodos dentro de cada cubo 
    // Disposición isométrica de cubo 3D proyectado en 2D
    // nodo i (0..7): bits b2(vertical) b1(diagonal) b0(horizontal)
    static final int[][] NODE_POS = {
        // {x, y}  — relativo al origen del cubo, en un área de 300x260
        {100, 190},  // 000
        {200, 190},  // 001
        {150, 230},  // 010
        {250, 230},  // 011
        {100,  90},  // 100
        {200,  90},  // 101
        {150, 130},  // 110
        {250, 130},  // 111
    };

    // Aristas del hipercubo 3D (solo dentro de un cubo, 12 aristas)
    static final int[][] CUBE_EDGES = {
        {0,1},{2,3},{4,5},{6,7},   // horizontales (bit 0)
        {0,2},{1,3},{4,6},{5,7},   // diagonales   (bit 1)
        {0,4},{1,5},{2,6},{3,7},   // verticales   (bit 2)
    };

    // Main 
    public static void main(String[] args) {
        SwingUtilities.invokeLater(HipercuboRed::new);
    }

    HipercuboRed() {
        super("Red de Hipercubos ;/");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(BG);

        // Encabezado
        JPanel encabezado = crearEncabezado();
        add(encabezado, BorderLayout.NORTH);

        // Centro: cubos
        cubePanel = new CubePanel();
        add(cubePanel, BorderLayout.CENTER);

        // Panel derecho: controles 
        JPanel derecho = crearPanelDerecho();
        add(derecho, BorderLayout.EAST);

        setSize(1100, 680);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Encabezado 
    JPanel crearEncabezado() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(12, 16, 28));
        p.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(30, 50, 90)));

        JLabel title = new JLabel("RED DE HIPERCUBOS", SwingConstants.LEFT);
        title.setFont(new Font("Monospaced", Font.BOLD, 18));
        title.setForeground(ACCENT);

        p.add(title, BorderLayout.WEST);
        return p;
    }

    //  Panel derecho 
    JPanel crearPanelDerecho() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(PANEL_BG);
        p.setPreferredSize(new Dimension(300, 0));

        // Selección de nodos
        p.add(sectionLabel("RUTA"));
        p.add(Box.createVerticalStrut(6));

        // Fuente
        JPanel srcRow = labeledCombo("Emisor (Fuente):", srcBox = buildNodeCombo());
        p.add(srcRow);
        p.add(Box.createVerticalStrut(4));

        // Destino
        JPanel dstRow = labeledCombo("Destino:", dstBox = buildNodeCombo());
        p.add(dstRow);
        p.add(Box.createVerticalStrut(12));

        // Botón rutear
        JButton routeBtn = styledButton("CALCULAR RUTA");
        routeBtn.addActionListener(e -> doRoute());
        p.add(wrapCenter(routeBtn));
        p.add(Box.createVerticalStrut(4));

        // Botón reset
        JButton resetBtn = styledButtonGhost("LIMPIAR");
        resetBtn.addActionListener(e -> doReset());
        p.add(wrapCenter(resetBtn));
        p.add(Box.createVerticalStrut(14));

        // ── Leyenda ──
        p.add(sectionLabel("LEYENDA"));
        p.add(legendRow(NODOEMISOR,   "Nodo Emisor"));
        p.add(legendRow(NODODESTINO,   "Nodo Destino"));
        p.add(legendRow(NODORUTA,  "Nodo en Ruta"));
        p.add(legendRow(ARISTARUTA,  "Arista de Ruta"));
        p.add(legendRow(ARISTASALTO, "Salto entre Cubos (bit 3)"));
        p.add(Box.createVerticalStrut(8));

        //  Status 
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 11));
        statusLabel.setForeground(ACCENT);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 6, 8));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(statusLabel);

        return p;
    }

    JComboBox<String> buildNodeCombo() {
        String[] items = new String[17];
        items[0] = "-- seleccionar --";
        // 16 nodos: cubo 0 (nodos 0-7, bit3=0), cubo 1 (nodos 8-15, bit3=1)
        for (int i = 0; i < 8; i++) {
            items[i + 1] = String.format("C0·N%d  [%s]", i, bits4(i));
        }
        for (int i = 0; i < 8; i++) {
            items[i + 9] = String.format("C1·N%d  [%s]", i, bits4(i | 8));
        }
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(new Color(20, 28, 50));
        cb.setForeground(TEXT_MAIN);
        cb.setFont(new Font("Monospaced", Font.PLAIN, 11));
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return cb;
    }

    JPanel labeledCombo(String label, JComboBox<String> cb) {
        JPanel p = new JPanel(new BorderLayout(4, 2));
        p.setBackground(PANEL_BG);
        p.setBorder(BorderFactory.createEmptyBorder(2, 16, 2, 12));
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lbl.setForeground(TEXT_DIM);
        p.add(lbl, BorderLayout.NORTH);
        p.add(cb,  BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        return p;
    }

    JPanel legendRow(Color c, String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        p.setBackground(PANEL_BG);
        JLabel dot = new JLabel("●");
        dot.setForeground(c);
        dot.setFont(new Font("Dialog", Font.PLAIN, 14));
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lbl.setForeground(TEXT_MAIN);
        p.add(dot); p.add(lbl);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return p;
    }

    JPanel dimRow(String bit, String name) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 1));
        p.setBackground(PANEL_BG);
        JLabel b = new JLabel(bit);
        b.setFont(new Font("Monospaced", Font.BOLD, 10));
        b.setForeground(ACCENT);
        JLabel n = new JLabel("→ " + name);
        n.setFont(new Font("Monospaced", Font.PLAIN, 10));
        n.setForeground(TEXT_DIM);
        p.add(b); p.add(n);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return p;
    }

    JLabel sectionLabel(String t) {
        JLabel l = new JLabel("  " + t);
        l.setFont(new Font("Monospaced", Font.BOLD, 9));
        l.setForeground(new Color(60, 90, 140));
        l.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(22, 35, 65)));
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    JButton styledButton(String t) {
        JButton b = new JButton(t);
        b.setFont(new Font("Monospaced", Font.BOLD, 12));
        b.setBackground(ACCENT);
        b.setForeground(new Color(5, 10, 20));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(220, 34));
        return b;
    }

    JButton styledButtonGhost(String t) {
        JButton b = new JButton(t);
        b.setFont(new Font("Monospaced", Font.PLAIN, 11));
        b.setBackground(new Color(22, 30, 55));
        b.setForeground(TEXT_DIM);
        b.setFocusPainted(false);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createLineBorder(new Color(35, 55, 100)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(220, 28));
        return b;
    }

    JPanel wrapCenter(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setBackground(PANEL_BG);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        p.add(c);
        return p;
    }

    // ── Lógica de Ruteo ──────────────────────────────────────────

    /**
     * Algoritmo de enrutamiento con tabla de verdad.
     *
     * Tabla de verdad (XOR por bit):
     *   bit_actual XOR bit_destino = 1  →  esa dimensión debe corregirse
     *   bit_actual XOR bit_destino = 0  →  esa dimensión ya está bien
     *
     * Entre las dimensiones que deben corregirse, se elige UNA al azar
     * (enrutamiento aleatorio entre caminos óptimos).
     * El proceso se repite hasta llegar al destino.
     */
    void doRoute() {
        int si = srcBox.getSelectedIndex();
        int di = dstBox.getSelectedIndex();
        if (si == 0 || di == 0) {
            statusLabel.setText("Selecciona emisor y destino.");
            return;
        }
        // índice global: 0-7 = cubo0, 8-15 = cubo1
        source = si - 1;  // 0..15
        dest   = di - 1;

        if (source == dest) {
            statusLabel.setText("Emisor = Destino.");
            return;
        }

        path.clear();
        pathEdges.clear();
        Random rng = new Random();

       System.out.println("=== TABLA DE VERDAD ===");
       System.out.println(String.format("Emisor : Nodo %-2d [%s]", source, bits4(source)));
       System.out.println(String.format("Destino: Nodo %-2d [%s]", dest, bits4(dest)));
  


        int cur = source;
        path.add(cur);
        int maxSteps = 20; // seguridad
        while (cur != dest && maxSteps-- > 0) {
            int xorVal = cur ^ dest;
             System.out.println(String.format("\nNodo actual : [%s]", bits4(cur)));
             System.out.println(String.format("Destino     : [%s]", bits4(dest)));
             System.out.println(String.format("XOR         : [%s]", bits4(xorVal)));
             System.out.print("Bits a corregir: ");

            List<Integer> dims = new ArrayList<>();
            for (int bit = 0; bit < 4; bit++) {
                if (((xorVal >> bit) & 1) == 1) {
                    dims.add(bit);
                     System.out.print("bit" + bit + " ");
                }
            }
            System.out.println("");
            
            int chosen = dims.get(rng.nextInt(dims.size()));
            int next = cur ^ (1 << chosen);

            String dimName = new String[]{"horizontal","diagonal","vertical","salto"}[chosen];
           System.out.println(String.format("Elegido bit%d (%s): %s→%s",
        chosen, dimName, bits4(cur), bits4(next)));


            pathEdges.add(new int[]{cur, next});
            cur = next;
            path.add(cur);
        }

        System.out.println("\n=== RUTA FINAL ===");
        System.out.println("Pasos: " + (path.size() - 1));
        StringBuilder ruta = new StringBuilder();
        for (int n : path) ruta.append("[").append(bits4(n)).append("] → ");
        String rs = ruta.toString();
        System.out.println(rs.endsWith(" → ") ? rs.substring(0, rs.length()-4) : rs);
        statusLabel.setText("Ruta calculada: " + (path.size()-1) + " salto(s)");
        cubePanel.repaint();
    }

    void doReset() {
        source = -1; dest = -1;
        path.clear(); pathEdges.clear();
        routeLog = "";
        statusLabel.setText(" ");
        srcBox.setSelectedIndex(0);
        dstBox.setSelectedIndex(0);
        cubePanel.repaint();
    }

    // ── Helpers ──────────────────────────────────────────────────
    static String bits4(int n) {
        return String.format("%d%d%d%d",
            (n>>3)&1, (n>>2)&1, (n>>1)&1, n&1);
    }

    boolean edgeInPath(int u, int v) {
        for (int[] e : pathEdges) {
            if ((e[0]==u && e[1]==v)||(e[0]==v && e[1]==u)) return true;
        }
        return false;
    }

    // ── Panel de dibujo ──────────────────────────────────────────
    class CubePanel extends JPanel {
        // Origen de cada cubo (esquina superior-izquierda del área de dibujo)
        static final int CUBE0_X = 30,  CUBE0_Y = 50;
        static final int CUBE1_X = 370, CUBE1_Y = 50;
        static final int CUBE_W  = 310, CUBE_H  = 300;
        static final int NODE_R  = 18;

        CubePanel() {
            setBackground(BG);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawCube(g, CUBE0_X, CUBE0_Y, 0);
            drawCube(g, CUBE1_X, CUBE1_Y, 1);
            drawCrossLinks(g);
            dibujarNodos(g, CUBE0_X, CUBE0_Y, 0);
            dibujarNodos(g, CUBE1_X, CUBE1_Y, 1);
            drawRouteInfo(g);
        }


        void drawCube(Graphics2D g, int ox, int oy, int cubeIdx) {
            // Offset global: cubo0 = nodos 0-7, cubo1 = nodos 8-15
            int base = cubeIdx * 8;

            for (int[] e : CUBE_EDGES) {
                int u = base + e[0], v = base + e[1];
                Point pu = nodePoint(e[0], ox, oy);
                Point pv = nodePoint(e[1], ox, oy);

                boolean inPath = edgeInPath(u, v);
                int dimBit = Integer.numberOfTrailingZeros(e[0] ^ e[1]);
                Color edgeColor = inPath ? ARISTARUTA
                    : dimBit == 0 ? new Color(50,70,120)
                    : dimBit == 1 ? new Color(60,50,120)
                    :               new Color(40,70,100);

                g.setColor(edgeColor);
                g.setStroke(new BasicStroke(inPath ? 3f : 1.5f));
                g.drawLine(pu.x, pu.y, pv.x, pv.y);

                // Etiqueta de dimensión en aristas idle
                if (!inPath) {
                    int mx = (pu.x + pv.x) / 2, my = (pu.y + pv.y) / 2;
                    String[] dnames = {"H","D","V"};
                    g.setFont(new Font("Monospaced", Font.PLAIN, 8));
                    g.setColor(new Color(60, 80, 130));
                    g.drawString(dnames[dimBit], mx + 2, my - 2);
                }
            }
            g.setStroke(new BasicStroke(1));
        }

        void drawCrossLinks(Graphics2D g) {
            // Aristas de salto (bit3): nodo i en cubo0 ↔ nodo i en cubo1
            // Solo se dibujan 4 aristas visibles para no saturar (los 8 pares existen)
            int[] visible = {0, 1, 4, 5}; // esquinas más visibles
            for (int i : visible) {
                int u = i, v = i + 8;
                Point pu = nodePoint(i, CUBE0_X, CUBE0_Y);
                Point pv = nodePoint(i, CUBE1_X, CUBE1_Y);
                boolean inPath = edgeInPath(u, v);

                g.setColor(inPath ? new Color(0,240,180) : new Color(80, 30, 140));
                g.setStroke(new BasicStroke(inPath ? 3f : 1f,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, inPath ? null : new float[]{5, 6}, 0));
                g.drawLine(pu.x, pu.y, pv.x, pv.y);

                if (!inPath) {
                    int mx = (pu.x + pv.x) / 2, my = (pu.y + pv.y) / 2;
                    g.setFont(new Font("Monospaced", Font.PLAIN, 8));
                    g.setColor(new Color(100, 40, 160));
                    g.drawString("S", mx + 2, my - 2);
                }
            }
            // También dibujar las lineas de cruce que estén en la ruta pero no en visible[]
            for (int[] e : pathEdges) {
                int bit = Integer.numberOfTrailingZeros(e[0] ^ e[1]);
                if (bit == 3) {
                    int localU = e[0] & 7, localV = e[1] & 7;
                    boolean alreadyDrawn = false;
                    for (int vi : visible) if (vi == localU || vi == localV) { alreadyDrawn = true; break; }
                    if (!alreadyDrawn) {
                        int cubeU = (e[0] >> 3) & 1;
                        Point pu = nodePoint(localU, cubeU == 0 ? CUBE0_X : CUBE1_X, cubeU == 0 ? CUBE0_Y : CUBE1_Y);
                        Point pv = nodePoint(localV, cubeU == 0 ? CUBE1_X : CUBE0_X, cubeU == 0 ? CUBE1_Y : CUBE0_Y);
                        g.setColor(new Color(0, 240, 180));
                        g.setStroke(new BasicStroke(3f));
                        g.drawLine(pu.x, pu.y, pv.x, pv.y);
                    }
                }
            }
            g.setStroke(new BasicStroke(1));
        }

        void dibujarNodos(Graphics2D g, int ox, int oy, int cubeIdx) {
            int base = cubeIdx * 8;
            for (int i = 0; i < 8; i++) {
                int global = base + i;
                Point p = nodePoint(i, ox, oy);

                // Color del nodo
                Color fill;
                if (global == source) fill = NODOEMISOR;
                else if (global == dest) fill = NODODESTINO;
                else if (path.contains(global)) fill = NODORUTA;
                else fill = NODE_IDLE;

                // Sombra
                g.setColor(new Color(0,0,0,80));
                g.fillOval(p.x - NODE_R + 2, p.y - NODE_R + 2, NODE_R*2, NODE_R*2);

                // Relleno
                g.setColor(fill);
                g.fillOval(p.x - NODE_R, p.y - NODE_R, NODE_R*2, NODE_R*2);

                // Borde
                boolean highlighted = path.contains(global);
                g.setColor(highlighted ? fill.brighter() : new Color(80, 110, 180));
                g.setStroke(new BasicStroke(highlighted ? 2.5f : 1f));
                g.drawOval(p.x - NODE_R, p.y - NODE_R, NODE_R*2, NODE_R*2);
                g.setStroke(new BasicStroke(1));

                // ID en 4 bits
                g.setFont(new Font("Monospaced", Font.BOLD, 9));
                g.setColor(global == source || global == dest ? BG : TEXT_MAIN);
                String label = bits4(global);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(label, p.x - fm.stringWidth(label)/2, p.y + 4);

                // Número local debajo
                g.setFont(new Font("Monospaced", Font.PLAIN, 8));
                g.setColor(TEXT_DIM);
                String idLabel = "N" + i;
                g.drawString(idLabel, p.x - fm.stringWidth(idLabel)/2, p.y + NODE_R + 10);
            }
        }

        void drawRouteInfo(Graphics2D g) {
            if (path.size() < 2) return;
            // Flecha de paso en cada arista de la ruta
            g.setColor(new Color(0, 255, 160, 180));
            for (int[] e : pathEdges) {
                Point pu = globalPoint(e[0]);
                Point pv = globalPoint(e[1]);
                drawArrow(g, pu, pv);
            }
        }

        void drawArrow(Graphics2D g, Point from, Point to) {
            double dx = to.x - from.x, dy = to.y - from.y;
            double len = Math.sqrt(dx*dx + dy*dy);
            if (len == 0) return;
            double mx = (from.x + to.x) / 2.0, my = (from.y + to.y) / 2.0;
            double nx = dx / len, ny = dy / len;
            double size = 8;
            int[] xs = {(int)(mx + nx*size), (int)(mx - nx*size/2 - ny*size/2), (int)(mx - nx*size/2 + ny*size/2)};
            int[] ys = {(int)(my + ny*size), (int)(my - ny*size/2 + nx*size/2), (int)(my - ny*size/2 - nx*size/2)};
            g.fillPolygon(xs, ys, 3);
        }

        Point nodePoint(int localIdx, int ox, int oy) {
            return new Point(ox + NODE_POS[localIdx][0], oy + NODE_POS[localIdx][1]);
        }

        Point globalPoint(int globalIdx) {
            int cube = (globalIdx >> 3) & 1;
            int local = globalIdx & 7;
            int ox = cube == 0 ? CUBE0_X : CUBE1_X;
            int oy = cube == 0 ? CUBE0_Y : CUBE1_Y;
            return nodePoint(local, ox, oy);
        }
    }
}
