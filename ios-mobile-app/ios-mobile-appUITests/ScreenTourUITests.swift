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
