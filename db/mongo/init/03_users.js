// =============================================================================
// CineConecta · MongoDB · 03_users.js
// Usuarios del motor con mínimo privilegio.
//
// SOLO PARA MONGODB LOCAL (Docker). En Atlas, los usuarios y roles se crean
// desde la interfaz (Database Access), con los mismos permisos de abajo.
//
// Contraseñas: se toman de variables de entorno; los valores por defecto son
// solo para desarrollo local.
// =============================================================================

const DB_NAME = (typeof process !== 'undefined' && (process.env.CINE_DB || process.env.MONGO_INITDB_DATABASE)) || 'cineconecta';
const cine = db.getSiblingDB(DB_NAME);
const env = (name, fallback) => (typeof process !== 'undefined' && process.env[name]) || fallback;

// Rol de la aplicación: permisos por colección.
//   - admin_users es de SOLO LECTURA: la aplicación no puede crear administradores;
//     el primero lo crea 04_admin.js con la carga inicial.
//   - Ninguna colección permite "remove": nada se borra. Quitar de "Mi lista" es
//     un update ($pull) sobre el documento del perfil.
const appPrivileges = [
  { resource: { db: DB_NAME, collection: 'contents' }, actions: ['find', 'insert', 'update'] },
  { resource: { db: DB_NAME, collection: 'my_lists' }, actions: ['find', 'insert', 'update'] },
  { resource: { db: DB_NAME, collection: 'playback_sessions' }, actions: ['find', 'insert', 'update'] },
  { resource: { db: DB_NAME, collection: 'admin_users' }, actions: ['find'] },
];

if (cine.getRole('cineAppRole') === null) {
  cine.createRole({ role: 'cineAppRole', privileges: appPrivileges, roles: [] });
  print('  rol cineAppRole: creado');
} else {
  cine.updateRole('cineAppRole', { privileges: appPrivileges, roles: [] });
  print('  rol cineAppRole: actualizado');
}

function ensureUser(user, pwd, roles) {
  if (cine.getUser(user) === null) {
    cine.createUser({ user, pwd, roles });
    print(`  usuario ${user}: creado`);
  } else {
    cine.updateUser(user, { roles });
    print(`  usuario ${user}: ya existía (roles actualizados)`);
  }
}

// Dueño: colecciones, validadores, índices y cargas. La aplicación no lo usa.
ensureUser('cineOwner', env('CINE_OWNER_PWD', 'cine_owner_dev'), [{ role: 'dbOwner', db: DB_NAME }]);
// Conexión de la aplicación.
ensureUser('cineApp', env('CINE_APP_PWD', 'cine_app_dev'), [{ role: 'cineAppRole', db: DB_NAME }]);
// Informes y analítica: solo lectura.
ensureUser('cineReport', env('CINE_REPORT_PWD', 'cine_report_dev'), [{ role: 'read', db: DB_NAME }]);
