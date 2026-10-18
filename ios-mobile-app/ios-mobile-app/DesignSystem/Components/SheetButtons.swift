import SwiftUI

// Botones de las barras de las hojas. En iOS 26 el sistema dibuja los suyos:
// cerrar y cancelar como ✕ de vidrio y confirmar como acción prominente con el acento.
// En iOS 18 se usan los textos clásicos.

/// Cierra una hoja que no edita nada (Cuenta, evaluación con borrador guardado).
struct SheetCloseButton: View {
    let action: () -> Void

    var body: some View {
        if #available(iOS 26, *) {
            Button(role: .close, action: action)
        } else {
            Button("Cerrar", action: action)
        }
    }
}

/// Descarta lo que se estaba editando en una hoja.
struct SheetCancelButton: View {
    let action: () -> Void

    var body: some View {
        if #available(iOS 26, *) {
            Button(role: .cancel, action: action)
        } else {
            Button("Cancelar", role: .cancel, action: action)
        }
    }
}

/// Confirma la acción de una hoja con un nombre específico ("Aprobar", "Guardar").
struct SheetConfirmButton: View {
    let title: String
    var isBusy = false
    let action: () -> Void

    var body: some View {
        if #available(iOS 26, *) {
            Button(role: .confirm, action: action) { label }
        } else {
            Button(action: action) { label.fontWeight(.semibold) }
        }
    }

    @ViewBuilder
    private var label: some View {
        if isBusy {
            ProgressView()
                .accessibilityLabel("Guardando")
        } else {
            Text(title)
        }
    }
}
