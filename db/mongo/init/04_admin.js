// =============================================================================
// CineConecta · MongoDB · 04_admin.js
// Administrador inicial (carga inicial de la base de datos).
//
// La aplicación no puede crear administradores (cineApp solo lee admin_users),
// así que el primero se crea aquí, solo si la colección está vacía.
//
// La contraseña llega ya como hash BCrypt (compatible con BCryptPasswordEncoder
// de Spring). Por defecto, el hash corresponde a la contraseña de desarrollo
// "Admin123*". Para otro entorno, genere un hash nuevo y páselo en
// ADMIN_PASSWORD_HASH; nunca suba la contraseña en claro al repositorio.
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);
const env = (name, fallback) => (typeof process !== 'undefined' && process.env[name]) || fallback;

const DEV_HASH = '$2a$10$uHe.TsRfEHjqT6oQ.g63w.3oJuIbfL8j/AdEQxuxlWlOTaTJsZBsy'; // "Admin123*"

if (cine.admin_users.countDocuments({}) === 0) {
  cine.admin_users.insertOne({
    name: env('ADMIN_NAME', 'Administrador CineConecta'),
    email: env('ADMIN_EMAIL', 'admin@cineconecta.local').toLowerCase(),
    passwordHash: env('ADMIN_PASSWORD_HASH', DEV_HASH),
    createdAt: new Date(),
  });
  print('  administrador inicial: creado');
} else {
  print('  administrador inicial: ya existe un administrador, no se crea otro');
}
