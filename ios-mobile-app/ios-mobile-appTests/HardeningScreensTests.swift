import SwiftUI
import UIKit
import XCTest
@testable import ios_mobile_app

/// Capturas de las vistas reales con servicio sintético exclusivo de pruebas.
/// No añade una entrada de demostración a la aplicación ni usa credenciales reales.
nonisolated final class HardeningScreensTests: XCTestCase {
    @MainActor
    func testHardenedScreens() async throws {
        try await renderScreens(landscape: false)
    }

    @MainActor
    func testPolishedLandscapeScreens() async throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom == .pad,
                      "El modo de ventanas de iPad requiere rotación física, cubierta por ScreenTourUITests")
        try await renderScreens(landscape: true)
    }

    @MainActor
    private func renderScreens(landscape: Bool) async throws {
        for (scheme, size, suffix) in [(ColorScheme.light, DynamicTypeSize.large, "light"),
                                       (.dark, .accessibility5, "dark-accessibility")] {
            let suffix = suffix + (landscape ? "-landscape" : "")
            let service = HardeningService()
            let session = SessionStore(user: DemoScenario.analyst.user,
                                       configuration: AuthConfiguration(server: AuthConfiguration.defaultServer), service: service)
            await session.bootstrap()
            await session.clients.refresh()
            await service.failApplicants(.transport(.notConnectedToInternet))
            await session.clients.refresh()
            try await capture(ClientsView().environment(session).environment(session.router),
                              name: "clients-error-\(suffix)", scheme: scheme, size: size, landscape: landscape)

            session.reviewQueue.filter = .partial
            try await capture(ReviewQueueView().environment(session).environment(session.router),
                              name: "review-filter-\(suffix)", scheme: scheme, size: size, landscape: landscape)

            await service.failApplicants(nil)
            let applicants = try await service.applicants(page: 1, size: 1, segment: nil, document: nil)
            let applicant = try XCTUnwrap(applicants.items.first)
            session.startEvaluation(document: applicant.document, applicant: applicant)
            session.router.isEvaluationPresented = false
            try await capture(EvaluationFlowView().environment(session),
                              name: "existing-applicant-\(suffix)", scheme: scheme, size: size, landscape: landscape)

            let assessments = try await service.assessments(applicantId: nil, since: nil, page: 1, size: 1)
            let assessmentId = try XCTUnwrap(assessments.items.first?.id)
            let assessment = try await service.assessment(id: assessmentId)
            try await capture(NavigationStack { AssessmentResultList(assessment: assessment) },
                              name: "result-\(suffix)", scheme: scheme, size: size, landscape: landscape)

            session.startEvaluation(document: "CI-OTRO-CLIENTE")
            try await capture(MainTabView().environment(session).environment(session.router),
                              name: "draft-conflict-\(suffix)", scheme: scheme, size: size, landscape: landscape)

            let defaults = try XCTUnwrap(UserDefaults(suiteName: "hardening-screen-\(UUID().uuidString)"))
            try await capture(WelcomeView().environment(AppModel(defaults: defaults)),
                              name: "welcome-\(suffix)", scheme: scheme, size: size, landscape: landscape)
        }
    }

    @MainActor
    private func capture<Content: View>(_ content: Content, name: String,
                                       scheme: ColorScheme, size: DynamicTypeSize, landscape: Bool) async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        window.overrideUserInterfaceStyle = scheme == .dark ? .dark : .light
        window.traitOverrides.preferredContentSizeCategory = size.isAccessibilitySize
            ? .accessibilityExtraExtraExtraLarge : .large
        let controller = UIHostingController(rootView: content
            .environment(\.locale, .bolivia)
            .preferredColorScheme(scheme)
            .dynamicTypeSize(size))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        if UIDevice.current.userInterfaceIdiom != .pad {
            controller.setNeedsUpdateOfSupportedInterfaceOrientations()
            scene.requestGeometryUpdate(.iOS(interfaceOrientations: landscape ? .landscapeLeft : .portrait)) { error in
                XCTFail("No se pudo orientar la escena: \(error.localizedDescription)")
            }
        }
        defer {
            window.isHidden = true
            window.rootViewController = nil
            previous?.makeKeyAndVisible()
        }
        try await Task.sleep(for: .milliseconds(450))
        controller.view.layoutIfNeeded()
        XCTAssertGreaterThan(window.bounds.width, 0)
        XCTAssertEqual(window.bounds.width > window.bounds.height, landscape,
                       "La captura debe usar la orientación real solicitada")
        func attach(_ label: String) {
            let screenshot = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                window.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
            }
            let attachment = XCTAttachment(image: screenshot)
            attachment.name = label
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        attach(name)
        if size.isAccessibilitySize {
            func scrollViews(in view: UIView) -> [UIScrollView] {
                (view as? UIScrollView).map { [$0] } ?? view.subviews.flatMap { scrollViews(in: $0) }
            }
            let presentedView = controller.presentedViewController?.view ?? controller.view!
            let scroll = scrollViews(in: presentedView).filter {
                $0.contentSize.height > $0.bounds.height
            }.max { $0.bounds.height < $1.bounds.height }
            if let scroll {
                let bottom = max(-scroll.adjustedContentInset.top,
                                 scroll.contentSize.height - scroll.bounds.height + scroll.adjustedContentInset.bottom)
                if name.hasPrefix("welcome") {
                    scroll.setContentOffset(CGPoint(x: 0, y: bottom * 0.45), animated: false)
                    try await Task.sleep(for: .milliseconds(200))
                    attach(name + "-middle")
                }
                // List estima la altura de filas aún no materializadas; volver a resolver
                // el extremo permite capturar el pie real después de medir el texto grande.
                for _ in 0..<6 {
                    let end = max(-scroll.adjustedContentInset.top,
                                  scroll.contentSize.height - scroll.bounds.height + scroll.adjustedContentInset.bottom)
                    scroll.setContentOffset(CGPoint(x: 0, y: end), animated: false)
                    try await Task.sleep(for: .milliseconds(100))
                    controller.view.layoutIfNeeded()
                    if abs(scroll.contentSize.height - scroll.bounds.height + scroll.adjustedContentInset.bottom - end) < 1 { break }
                }
                attach(name + "-bottom")
            } else if name.hasPrefix("welcome") {
                XCTFail("La bienvenida con texto máximo debe permitir desplazarse hasta el acceso")
            }
        }
    }
}
