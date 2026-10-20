import XCTest

/// Verifica la entrada real sin registrar datos ni usar credenciales de prueba.
final class ScreenTourUITests: XCTestCase {
    @MainActor
    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-DCSignedOut", "YES"] + extra
        app.launch()
        return app
    }

    @MainActor
    func testWelcomeOnlyOffersRealSignIn() {
        let app = launch()
        XCTAssertTrue(app.buttons["Iniciar sesión con tu institución"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["Explorar con datos de demostración"].exists)
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@", "doggycredit.los5furiosos.cloud")).firstMatch.exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Acceso al servidor"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testLegacyDemoArgumentCannotBypassAuthentication() {
        let app = launch(["-DCDemo", "analyst", "-DCOpen", "evaluation-result"])
        XCTAssertTrue(app.buttons["Iniciar sesión con tu institución"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.tabBars.firstMatch.exists)
    }

    @MainActor
    func testMaximumTextKeepsSignInAndServerReachable() {
        XCUIDevice.shared.orientation = .portrait
        defer { XCUIDevice.shared.orientation = .portrait }
        let app = launch(["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        let signIn = app.buttons["welcome.sign-in"]
        XCTAssertTrue(signIn.waitForExistence(timeout: 10))
        for _ in 0..<6 where !signIn.isHittable { app.swipeUp() }
        XCTAssertTrue(signIn.isHittable, "El acceso debe quedar al alcance al desplazarse con texto máximo")

        let server = app.buttons["welcome.connection"]
        for _ in 0..<4 where !server.isHittable { app.swipeUp() }
        XCTAssertTrue(server.isHittable)
        server.tap()
        let domain = app.staticTexts["doggycredit.los5furiosos.cloud"]
        XCTAssertTrue(domain.waitForExistence(timeout: 3))
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Acceso y servidor con texto máximo"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testWelcomeInLandscape() {
        XCUIDevice.shared.orientation = .landscapeLeft
        defer { XCUIDevice.shared.orientation = .portrait }
        let app = launch()
        let signIn = app.buttons["welcome.sign-in"]
        XCTAssertTrue(signIn.waitForExistence(timeout: 10))
        XCTAssertGreaterThan(app.frame.width, app.frame.height, "La ventana debe estar en horizontal")
        for _ in 0..<5 where !signIn.isHittable { app.swipeUp() }
        XCTAssertTrue(signIn.isHittable)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "Acceso en horizontal"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testSignInOpensDeployedKeycloak() {
        let app = launch()
        let signIn = app.buttons["Iniciar sesión con tu institución"]
        XCTAssertTrue(signIn.waitForExistence(timeout: 10))
        signIn.tap()
        // ASWebAuthenticationSession puede pedir primero permiso del sistema.
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let continueButton = springboard.buttons.matching(NSPredicate(format: "label IN %@", ["Continue", "Continuar"])).firstMatch
        if continueButton.waitForExistence(timeout: 3) { continueButton.tap() }
        let username = app.webViews.textFields.firstMatch
        XCTAssertTrue(username.waitForExistence(timeout: 30), "Keycloak debe mostrar el formulario de la institución")
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Keycloak del VPS"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
