/**
 * Módulo de administración: autenticación de administradores y panel bajo {@code /admin/**}.
 *
 * <p>Los administradores viven en MongoDB ({@code admin_users}), son independientes de las
 * cuentas de clientes y la aplicación solo puede leerlos: el primero lo crea la carga inicial
 * de la base ({@code db/mongo/init/04_admin.js}).</p>
 *
 * <p>Toda ruta {@code /admin/**} pasa por la cadena de seguridad de este módulo, que exige el rol
 * ADMIN. Los demás módulos que agreguen pantallas de administración (catálogo, planes, informes)
 * deben publicarlas bajo {@code /admin/...} para quedar protegidas.</p>
 */
package com.cineconecta.platform.backoffice;
