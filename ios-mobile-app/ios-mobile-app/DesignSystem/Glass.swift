import SwiftUI

// Liquid Glass cuando el iPhone tiene iOS 26 o posterior; en iOS 18 los mismos
// componentes caen a materiales y estilos estándar del sistema. Las pantallas usan
// solo estos modificadores: así la decisión de plataforma vive en un único lugar.

nonisolated enum Platform {
    static var supportsLiquidGlass: Bool {
        if #available(iOS 26, *) { true } else { false }
    }

    /// El accesorio inferior del TabView (con `isEnabled`) llegó en iOS 26.1.
    static var supportsTabBottomAccessory: Bool {
        if #available(iOS 26.1, *) { true } else { false }
    }
}

extension View {
    /// Superficie de vidrio para controles flotantes; material translúcido antes de iOS 26.
    @ViewBuilder
    func dcGlass(tint: Color? = nil, interactive: Bool = false, in shape: some Shape = Capsule()) -> some View {
        if #available(iOS 26, *) {
            glassEffect(.regular.tint(tint).interactive(interactive), in: shape)
        } else {
            background(.regularMaterial, in: shape)
        }
    }

    /// Acción principal de un contexto: vidrio prominente teñido con el acento de marca.
    @ViewBuilder
    func dcProminentButtonStyle() -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.glassProminent)
        } else {
            buttonStyle(.borderedProminent)
        }
    }

    /// Acción secundaria sobre contenido: vidrio neutro.
    @ViewBuilder
    func dcSecondaryButtonStyle() -> some View {
        if #available(iOS 26, *) {
            buttonStyle(.glass)
        } else {
            buttonStyle(.bordered)
        }
    }

    /// La barra de pestañas se minimiza al desplazarse, como en Música (iOS 26).
    @ViewBuilder
    func dcMinimizeTabBarOnScroll() -> some View {
        if #available(iOS 26, *) {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }

    /// Barra inferior propia (acciones de un flujo). En iOS 26 el contenido que se desplaza
    /// debajo recibe el efecto de borde de Liquid Glass; antes, un material de barra.
    @ViewBuilder
    func dcBottomBar(@ViewBuilder content: () -> some View) -> some View {
        if #available(iOS 26, *) {
            safeAreaBar(edge: .bottom, spacing: 0, content: content)
        } else {
            safeAreaInset(edge: .bottom, spacing: 0) {
                content().background(.bar)
            }
        }
    }

    /// Barra superior propia bajo la de navegación (progreso de un flujo). En iOS 26 se
    /// integra con el efecto de borde de Liquid Glass, sin una banda de fondo propia.
    @ViewBuilder
    func dcTopBar(@ViewBuilder content: () -> some View) -> some View {
        if #available(iOS 26, *) {
            safeAreaBar(edge: .top, spacing: 0, content: content)
        } else {
            safeAreaInset(edge: .top, spacing: 0) {
                content().background(.bar)
            }
        }
    }

    /// Subtítulo bajo el título grande (iOS 26).
    @ViewBuilder
    func dcNavigationSubtitle(_ subtitle: String?) -> some View {
        if #available(iOS 26, *), let subtitle {
            navigationSubtitle(subtitle)
        } else {
            self
        }
    }

    /// Tarjeta de contenido agrupado, como las secciones de Ajustes o GitHub.
    func dcCard(padding: CGFloat = 16) -> some View {
        self
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: .rect(cornerRadius: Radius.card, style: .continuous))
    }
}

/// Agrupa elementos de vidrio para que se fundan entre sí al acercarse (iOS 26).
struct DCGlassGroup<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder var content: Content

    var body: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
    }
}
