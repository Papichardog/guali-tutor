package com.guali.tutor.form;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.Timer;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MainForm extends JFrame {

    private JMenuBar barra;
    private JMenu lecciones;
    private JMenuItem abrirLeccion;
    private JMenuItem cerrarLeccion;
    private JPanel mainPanel;
    private JMenuItem configuracion;
    private JMenuItem historial;
    private JMenuItem ayuda;
    private JLabel leccionActivaString;
    private JLabel leccionActivaTxt;
    private JSplitPane splitContenidoConsola;
    private JSplitPane splitLeccionEditor;
    private JPanel imagenPanel;
    private JEditorPane leccionMarkdownPane;
    private JPanel editorPlaceholder;
    private JPanel codigoEditorContainer;
    private RSyntaxTextArea codigoEditor;
    private JButton btnCompilar;
    private JButton btnEjecutar;
    private JScrollPane consolaScrollPane;
    private JTextArea consolaTexto;
    private JPanel leccionActivaPanel;
    private JButton btnZoomOut;
    private JButton btnZoomIn;
    private final List<Extension> markdownExtensions = List.of(TablesExtension.create());
    private final Parser markdownParser = Parser.builder().extensions(markdownExtensions).build();
    private final HtmlRenderer markdownRenderer = HtmlRenderer.builder().extensions(markdownExtensions).build();
    private String leccionMarkdownActual;
    private Timer temporizadorReajusteImagenes;
    private int ultimoAnchoRenderizado = -1;

    public MainForm() {
        setTitle("Guali Tutor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(mainPanel);
        setMinimumSize(new Dimension(1024, 768));
        consolaTexto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consolaScrollPane.setMinimumSize(new Dimension(0, 120));
        splitContenidoConsola.setOrientation(JSplitPane.VERTICAL_SPLIT);

        Font fuenteZoom = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
        Insets margenZoom = new Insets(0, 3, 0, 3);
        btnZoomOut.setFont(fuenteZoom);
        btnZoomOut.setMargin(margenZoom);
        btnZoomIn.setFont(fuenteZoom);
        btnZoomIn.setMargin(margenZoom);

        codigoEditor = new RSyntaxTextArea();
        codigoEditor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        codigoEditor.setCodeFoldingEnabled(true);
        codigoEditor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        codigoEditorContainer.setLayout(new BorderLayout());
        codigoEditorContainer.add(new RTextScrollPane(codigoEditor), BorderLayout.CENTER);

        leccionMarkdownPane = new JEditorPane() {
            // Sin esto, contenido que Swing no puede envolver (una tabla con una celda
            // ancha, por ejemplo) hace que todo el documento pida más ancho del que
            // tiene el panel, y el JScrollPane muestra una barra horizontal permanente.
            // Forzamos a que siempre ocupe el ancho del viewport para que ese contenido
            // se comprima/ajuste en vez de exigir una barra horizontal fija.
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true;
            }
        };
        leccionMarkdownPane.setEditable(false);
        leccionMarkdownPane.setContentType("text/html");
        HTMLEditorKit editorKit = new HTMLEditorKit();
        cargarEstiloMarkdown(editorKit.getStyleSheet());
        aplicarEstiloMarkdown(editorKit.getStyleSheet(), 13f);
        leccionMarkdownPane.setEditorKit(editorKit);
        leccionMarkdownPane.addHyperlinkListener(this::abrirEnlaceLeccion);
        JScrollPane leccionMarkdownScrollPane = new JScrollPane(leccionMarkdownPane);
        leccionMarkdownScrollPane.setPreferredSize(new Dimension(400, 300));
        // Ancho de viewport fijo: con AS_NEEDED, achicar la imagen podía acortar el
        // contenido lo suficiente para que la barra vertical desapareciera, lo que
        // ensanchaba el viewport y volvía a agrandar la imagen -> parpadeo en bucle.
        leccionMarkdownScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        temporizadorReajusteImagenes = new Timer(150, e -> renderizarLeccionMarkdown());
        temporizadorReajusteImagenes.setRepeats(false);
        leccionMarkdownScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                temporizadorReajusteImagenes.restart();
            }
        });
        imagenPanel.setLayout(new BorderLayout());
        imagenPanel.add(leccionMarkdownScrollPane, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null);
    }

    public void setBaseLeccion(URL base) {
        ((HTMLDocument) leccionMarkdownPane.getDocument()).setBase(base);
    }

    public void setLeccionMarkdown(String markdown) {
        leccionMarkdownActual = markdown;
        ultimoAnchoRenderizado = -1;
        renderizarLeccionMarkdown();
    }

    /**
     * El {@link javax.swing.text.html.ImageView} de Swing dibuja las imágenes al tamaño
     * fijado por el atributo HTML {@code width}/{@code height} (o al tamaño nativo si no
     * se especifica); ignora por completo reglas CSS como {@code max-width: 100%}. Por
     * eso, para que una imagen no se desborde del panel, hay que fijarle un {@code width}
     * explícito acorde al ancho disponible; Swing se encarga de escalar el alto
     * proporcionalmente. Se vuelve a renderizar en cada resize del viewport para que la
     * imagen se reajuste si el usuario cambia el tamaño de la ventana o el divisor.
     */
    private void renderizarLeccionMarkdown() {
        if (leccionMarkdownActual == null) {
            return;
        }
        int anchoDisponible = anchoDisponibleParaImagenes();
        if (anchoDisponible == ultimoAnchoRenderizado) {
            return;
        }
        ultimoAnchoRenderizado = anchoDisponible;
        Node documento = markdownParser.parse(leccionMarkdownActual);
        String html = markdownRenderer.render(documento);
        leccionMarkdownPane.setText(limitarAnchoImagenes(html, anchoDisponible));
        leccionMarkdownPane.setCaretPosition(0);
    }

    private String limitarAnchoImagenes(String html, int anchoDisponible) {
        if (anchoDisponible <= 0) {
            return html;
        }
        return html.replace("<img ", "<img width=\"" + anchoDisponible + "\" ");
    }

    private int anchoDisponibleParaImagenes() {
        Container padre = leccionMarkdownPane.getParent();
        int ancho = (padre instanceof JViewport) ? padre.getWidth() : leccionMarkdownPane.getWidth();
        Insets insets = leccionMarkdownPane.getInsets();
        ancho -= insets.left + insets.right;
        return Math.max(0, ancho);
    }

    private void abrirEnlaceLeccion(HyperlinkEvent evento) {
        if (evento.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
        }
        try {
            Desktop.getDesktop().browse(evento.getURL().toURI());
        } catch (Exception e) {
            appendConsola("No se pudo abrir el enlace: " + evento.getURL());
        }
    }

    public void appendConsola(String texto) {
        consolaTexto.append(texto + "\n");
    }

    public void setTamanoFuente(float tamano) {
        codigoEditor.setFont(codigoEditor.getFont().deriveFont(tamano));
        consolaTexto.setFont(consolaTexto.getFont().deriveFont(tamano));
        HTMLDocument documentoHtml = (HTMLDocument) leccionMarkdownPane.getDocument();
        aplicarEstiloMarkdown(documentoHtml.getStyleSheet(), tamano);
    }

    /**
     * Muestra un diálogo modal para capturar la ubicación del JDK.
     *
     * @return la ruta ingresada si el usuario presiona "Aceptar", o {@code null} si
     *         cancela o cierra el diálogo.
     */
    public String solicitarUbicacionJdk() {
        JTextField campoJdk = new JTextField(30);

        JPanel panelContenido = new JPanel(new BorderLayout(0, 12));
        panelContenido.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        panelContenido.add(new JLabel("Seleccione ubicación de su Java Development Kit (JDK)"), BorderLayout.NORTH);
        panelContenido.add(campoJdk, BorderLayout.CENTER);

        JButton btnAceptar = new JButton("Aceptar");
        JButton btnCancelar = new JButton("Cancelar");
        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 12));
        panelBotones.add(btnAceptar);
        panelBotones.add(btnCancelar);
        panelContenido.add(panelBotones, BorderLayout.SOUTH);

        JDialog dialogo = new JDialog(this, "Configuración", true);
        dialogo.setContentPane(panelContenido);
        dialogo.setResizable(false);

        String[] resultado = new String[1];
        btnAceptar.addActionListener(e -> {
            resultado[0] = campoJdk.getText();
            dialogo.dispose();
        });
        btnCancelar.addActionListener(e -> dialogo.dispose());

        dialogo.getRootPane().setDefaultButton(btnAceptar);
        dialogo.pack();
        dialogo.setLocationRelativeTo(this);
        dialogo.setVisible(true);

        return resultado[0];
    }

    private void aplicarEstiloMarkdown(StyleSheet hojaEstilos, float tamanoBase) {
        hojaEstilos.addRule("body, p, li { font-family: sans-serif; font-size: " + (int) tamanoBase + "pt; }");
        hojaEstilos.addRule("pre, code, tt { font-family: monospace; font-size: " + (int) (tamanoBase - 1) + "pt; }");
    }

    private void cargarEstiloMarkdown(StyleSheet hojaEstilos) {
        try (InputStream in = getClass().getResourceAsStream("/leccion-markdown.css")) {
            hojaEstilos.loadRules(new InputStreamReader(in, StandardCharsets.UTF_8), null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public JButton getBtnZoomIn() {
        return btnZoomIn;
    }

    public JButton getBtnZoomOut() {
        return btnZoomOut;
    }

    public JMenuItem getAbrirLeccion() {
        return abrirLeccion;
    }

    public JMenuItem getCerrarLeccion() {
        return cerrarLeccion;
    }

    public JMenuItem getConfiguracion() {
        return configuracion;
    }

    public JMenuItem getHistorial() {
        return historial;
    }

    public JMenuItem getAyuda() {
        return ayuda;
    }

    public JButton getBtnCompilar() {
        return btnCompilar;
    }

    public JButton getBtnEjecutar() {
        return btnEjecutar;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /** Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1));
        barra = new JMenuBar();
        barra.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        mainPanel.add(barra, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 2,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
                com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
                null, null, null, 0, false));
        lecciones = new JMenu();
        lecciones.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        lecciones.setText("Lecciones");
        barra.add(lecciones);
        abrirLeccion = new JMenuItem();
        abrirLeccion.setText("Abrir Lección");
        lecciones.add(abrirLeccion);
        cerrarLeccion = new JMenuItem();
        cerrarLeccion.setText("Cerrar Lección");
        lecciones.add(cerrarLeccion);
        configuracion = new JMenuItem();
        configuracion.setText("Configuración");
        barra.add(configuracion);
        historial = new JMenuItem();
        historial.setText("Historial");
        barra.add(historial);
        ayuda = new JMenuItem();
        ayuda.setText("Ayuda");
        barra.add(ayuda);
        final com.intellij.uiDesigner.core.Spacer spacer1 = new com.intellij.uiDesigner.core.Spacer();
        mainPanel.add(spacer1, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
                com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        leccionActivaPanel = new JPanel();
        leccionActivaPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        mainPanel.add(leccionActivaPanel, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 3,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
                com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        leccionActivaPanel.add(panel4, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
                com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        leccionActivaString = new JLabel();
        leccionActivaString.setBackground(new Color(-16579837));
        leccionActivaString.setForeground(new Color(-1));
        leccionActivaString.setOpaque(true);
        leccionActivaString.setText("  Lección Activa  ");
        panel4.add(leccionActivaString);
        final com.intellij.uiDesigner.core.Spacer spacer2 = new com.intellij.uiDesigner.core.Spacer();
        panel4.add(spacer2);
        leccionActivaTxt = new JLabel();
        leccionActivaTxt.setText("Lección 1");
        panel4.add(leccionActivaTxt);
        final com.intellij.uiDesigner.core.Spacer spacer3 = new com.intellij.uiDesigner.core.Spacer();
        leccionActivaPanel.add(spacer3, new com.intellij.uiDesigner.core.GridConstraints(0, 1, 1, 1,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
                com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        leccionActivaPanel.add(panel5, new com.intellij.uiDesigner.core.GridConstraints(0, 2, 1, 1,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
                com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnZoomOut = new JButton();
        btnZoomOut.setText("A−");
        panel5.add(btnZoomOut);
        btnZoomIn = new JButton();
        btnZoomIn.setText("A+");
        panel5.add(btnZoomIn);
        splitContenidoConsola = new JSplitPane();
        splitContenidoConsola.setResizeWeight(1.0);
        mainPanel.add(splitContenidoConsola, new com.intellij.uiDesigner.core.GridConstraints(2, 0, 1, 3,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
                com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        splitLeccionEditor = new JSplitPane();
        splitLeccionEditor.setDividerLocation(430);
        splitLeccionEditor.setResizeWeight(0.0);
        splitContenidoConsola.setLeftComponent(splitLeccionEditor);
        imagenPanel = new JPanel();
        imagenPanel.setLayout(new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitLeccionEditor.setLeftComponent(imagenPanel);
        editorPlaceholder = new JPanel();
        editorPlaceholder.setLayout(
                new com.intellij.uiDesigner.core.GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitLeccionEditor.setRightComponent(editorPlaceholder);
        codigoEditorContainer = new JPanel();
        codigoEditorContainer.setLayout(
                new com.intellij.uiDesigner.core.GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        editorPlaceholder.add(codigoEditorContainer, new com.intellij.uiDesigner.core.GridConstraints(0, 0, 1, 1,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
                com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW,
                null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        editorPlaceholder.add(panel2, new com.intellij.uiDesigner.core.GridConstraints(1, 0, 1, 1,
                com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
                com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
                com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        btnCompilar = new JButton();
        btnCompilar.setText("Compilar");
        panel2.add(btnCompilar);
        btnEjecutar = new JButton();
        btnEjecutar.setText("Ejecutar");
        panel2.add(btnEjecutar);
        consolaScrollPane = new JScrollPane();
        splitContenidoConsola.setRightComponent(consolaScrollPane);
        consolaTexto = new JTextArea();
        consolaTexto.setEditable(false);
        consolaTexto.setRows(6);
        consolaScrollPane.setViewportView(consolaTexto);
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() {
        return mainPanel;
    }

}
