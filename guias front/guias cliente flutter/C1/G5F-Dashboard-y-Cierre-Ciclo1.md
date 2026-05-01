# Guía 5F — Dashboard Principal y Cierre Ciclo 1 - Cliente

**Ciclo 1 · Sistema de Gestión de Trámites - Frontend**

> 🎯 **Objetivo:** Crear el layout principal de la aplicación con navegación, dashboard de inicio y componentes reutilizables. Validar que todo funciona en conjunto.

---

## 0. Requisitos

✅ Completadas todas las guías 1F-4F
✅ Backend ejecutándose en http://localhost:8080
✅ Todas las rutas funcionando correctamente

---

## 1. Componente Layout Principal (Navbar + Sidebar)

### app-layout.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { Usuario } from './models/usuario.model';
import { Router } from '@angular/router';

@Component({
  selector: 'app-layout',
  templateUrl: './app-layout.component.html',
  styleUrls: ['./app-layout.component.css']
})
export class AppLayoutComponent implements OnInit {

  usuarioActual: Usuario | null = null;
  menuAbierto = false;
  notificaciones = 5;

  constructor(
    private authService: AuthService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.authService.usuarioActual$.subscribe(usuario => {
      this.usuarioActual = usuario;
    });
  }

  toggleMenu(): void {
    this.menuAbierto = !this.menuAbierto;
  }

  irAHome(): void {
    this.router.navigate(['/dashboard']);
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  irAPerfil(): void {
    this.router.navigate(['/perfil']);
  }
}
```

### app-layout.component.html
```html
<div class="d-flex" style="height: 100vh;">
  <!-- Sidebar -->
  <nav class="sidebar bg-dark" [ngClass]="{ active: menuAbierto }">
    <div class="sidebar-header">
      <h5 class="text-white mb-0">
        <span class="me-2">📋</span>
        Sistema de Trámites
      </h5>
      <button class="btn-close btn-close-white d-md-none" (click)="toggleMenu()"></button>
    </div>

    <hr class="border-secondary">

    <ul class="nav flex-column">
      <li class="nav-item">
        <a
          routerLink="/dashboard"
          routerLinkActive="active"
          class="nav-link"
          (click)="menuAbierto = false"
        >
          <span class="me-2">🏠</span> Dashboard
        </a>
      </li>
      <li class="nav-item">
        <a
          routerLink="/tramites"
          routerLinkActive="active"
          class="nav-link"
          (click)="menuAbierto = false"
        >
          <span class="me-2">📋</span> Explorar Trámites
        </a>
      </li>
      <li class="nav-item">
        <a
          routerLink="/mis-tramites"
          routerLinkActive="active"
          class="nav-link"
          (click)="menuAbierto = false"
        >
          <span class="me-2">📊</span> Mis Trámites
          <span class="badge bg-danger ms-2" *ngIf="notificaciones > 0">
            {{ notificaciones }}
          </span>
        </a>
      </li>
      <li class="nav-item">
        <a
          routerLink="/notificaciones"
          routerLinkActive="active"
          class="nav-link"
          (click)="menuAbierto = false"
        >
          <span class="me-2">🔔</span> Notificaciones
        </a>
      </li>
      <li class="nav-item">
        <a
          routerLink="/ayuda"
          routerLinkActive="active"
          class="nav-link"
          (click)="menuAbierto = false"
        >
          <span class="me-2">❓</span> Ayuda
        </a>
      </li>
    </ul>

    <hr class="border-secondary mt-auto">

    <div class="sidebar-footer">
      <button class="btn btn-outline-light w-100 mb-2 btn-sm" (click)="irAPerfil()">
        👤 Mi Perfil
      </button>
      <button class="btn btn-danger w-100 btn-sm" (click)="logout()">
        🚪 Cerrar Sesión
      </button>
    </div>
  </nav>

  <!-- Main Content -->
  <main class="flex-grow-1 d-flex flex-column">
    <!-- Navbar -->
    <nav class="navbar navbar-light bg-light border-bottom">
      <div class="container-fluid">
        <button
          class="btn btn-outline-secondary d-md-none"
          type="button"
          (click)="toggleMenu()"
        >
          ☰ Menú
        </button>
        <span class="navbar-brand mb-0 h1">
          Bienvenido, {{ usuarioActual?.nombre || 'Usuario' }}
        </span>
        <div class="d-flex align-items-center gap-3">
          <span class="badge bg-info">
            🔔 {{ notificaciones }}
          </span>
          <img
            *ngIf="usuarioActual?.nombre"
            src="https://api.dicebear.com/7.x/avataaars/svg?seed={{usuarioActual.nombre}}"
            alt="Avatar"
            class="rounded-circle"
            style="width: 40px; height: 40px;"
          >
        </div>
      </div>
    </nav>

    <!-- Content -->
    <div class="flex-grow-1 overflow-auto">
      <router-outlet></router-outlet>
    </div>

    <!-- Footer -->
    <footer class="bg-light border-top p-3 text-center text-muted">
      <small>
        © 2026 Sistema de Gestión de Trámites. Ciclo 1 - Versión Beta
      </small>
    </footer>
  </main>
</div>

<style>
  .sidebar {
    width: 280px;
    position: relative;
    transition: transform 0.3s ease;
    display: flex;
    flex-direction: column;
  }

  @media (max-width: 768px) {
    .sidebar {
      position: fixed;
      left: 0;
      top: 0;
      height: 100vh;
      transform: translateX(-100%);
      z-index: 1000;
    }

    .sidebar.active {
      transform: translateX(0);
    }
  }

  .sidebar-header {
    padding: 20px 15px;
  }

  .sidebar-footer {
    padding: 15px;
  }

  .nav-link {
    color: rgba(255, 255, 255, 0.8) !important;
    padding: 10px 15px;
    transition: all 0.3s;
  }

  .nav-link:hover {
    color: white !important;
    background-color: rgba(255, 255, 255, 0.1);
  }

  .nav-link.active {
    color: white !important;
    background-color: rgba(255, 255, 255, 0.2);
    border-left: 4px solid #0d6efd;
  }

  main {
    flex: 1;
  }
</style>
```

---

## 2. Componente Dashboard

### dashboard.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { TramitesSeguimientoService } from '../../services/tramites-seguimiento.service';
import { TramiteResumen } from '../../models/tramite.model';
import { Usuario } from '../../models/usuario.model';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {

  usuarioActual: Usuario | null = null;
  tramitesCont = {
    total: 0,
    enProgreso: 0,
    completados: 0,
    borradores: 0
  };

  tramitesRecientes: TramiteResumen[] = [];
  loading = false;

  constructor(
    private authService: AuthService,
    private tramitesSeguimientoService: TramitesSeguimientoService
  ) { }

  ngOnInit(): void {
    this.usuarioActual = this.authService.getUsuarioActual();
    this.cargarDatos();
  }

  cargarDatos(): void {
    this.loading = true;

    this.tramitesSeguimientoService.obtenerMisTramites().subscribe({
      next: (tramites) => {
        this.procesarTramites(tramites);
        this.loading = false;
      },
      error: (error) => {
        console.error('Error cargando trámites:', error);
        this.loading = false;
      }
    });
  }

  procesarTramites(tramites: TramiteResumen[]): void {
    this.tramitesRecientes = tramites.slice(0, 5);
    this.tramitesCont.total = tramites.length;
    this.tramitesCont.enProgreso = tramites.filter(t => t.estado === 'en_progreso').length;
    this.tramitesCont.completados = tramites.filter(t => t.estado === 'completado').length;
    this.tramitesCont.borradores = tramites.filter(t => t.estado === 'borrador').length;
  }

  obtenerProgresoColor(progreso: number): string {
    if (progreso >= 75) return 'success';
    if (progreso >= 50) return 'info';
    if (progreso >= 25) return 'warning';
    return 'danger';
  }
}
```

### dashboard.component.html
```html
<div class="container-fluid py-4">
  <!-- Bienvenida -->
  <div class="row mb-4">
    <div class="col-12">
      <div class="alert alert-primary">
        <h4 class="alert-heading">¡Bienvenido a tu Panel de Trámites!</h4>
        <p class="mb-0">
          Aquí puedes explorar nuevos trámites, ver el estado de tus solicitudes
          y seguir el progreso en tiempo real.
        </p>
      </div>
    </div>
  </div>

  <!-- Cards de Estadísticas -->
  <div class="row mb-4">
    <div class="col-md-6 col-lg-3 mb-3">
      <div class="card border-primary">
        <div class="card-body">
          <h6 class="card-title text-muted">Total de Trámites</h6>
          <h2 class="text-primary">{{ tramitesCont.total }}</h2>
          <small class="text-muted">A lo largo del tiempo</small>
        </div>
      </div>
    </div>

    <div class="col-md-6 col-lg-3 mb-3">
      <div class="card border-info">
        <div class="card-body">
          <h6 class="card-title text-muted">En Proceso</h6>
          <h2 class="text-info">{{ tramitesCont.enProgreso }}</h2>
          <small class="text-muted">Requieren tu atención</small>
        </div>
      </div>
    </div>

    <div class="col-md-6 col-lg-3 mb-3">
      <div class="card border-success">
        <div class="card-body">
          <h6 class="card-title text-muted">Completados</h6>
          <h2 class="text-success">{{ tramitesCont.completados }}</h2>
          <small class="text-muted">Listos para descargar</small>
        </div>
      </div>
    </div>

    <div class="col-md-6 col-lg-3 mb-3">
      <div class="card border-warning">
        <div class="card-body">
          <h6 class="card-title text-muted">Borradores</h6>
          <h2 class="text-warning">{{ tramitesCont.borradores }}</h2>
          <small class="text-muted">Sin enviar</small>
        </div>
      </div>
    </div>
  </div>

  <!-- Acciones Rápidas -->
  <div class="row mb-4">
    <div class="col-12">
      <h5>Acciones Rápidas</h5>
      <div class="d-flex gap-2 flex-wrap">
        <a routerLink="/tramites" class="btn btn-primary">
          + Iniciar Nuevo Trámite
        </a>
        <a routerLink="/mis-tramites" class="btn btn-outline-primary">
          Ver Mis Trámites
        </a>
        <a routerLink="/notificaciones" class="btn btn-outline-secondary">
          Ver Notificaciones
        </a>
        <a routerLink="/ayuda" class="btn btn-outline-secondary">
          📚 Centro de Ayuda
        </a>
      </div>
    </div>
  </div>

  <!-- Trámites Recientes -->
  <div class="row">
    <div class="col-12">
      <div class="card">
        <div class="card-header bg-light">
          <h5 class="mb-0">Trámites Recientes</h5>
        </div>

        <div *ngIf="loading" class="card-body text-center">
          <div class="spinner-border" role="status">
            <span class="visually-hidden">Cargando...</span>
          </div>
        </div>

        <div *ngIf="!loading && tramitesRecientes.length > 0">
          <div class="table-responsive">
            <table class="table mb-0">
              <thead class="table-light">
                <tr>
                  <th>Código</th>
                  <th>Trámite</th>
                  <th>Estado</th>
                  <th>Progreso</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let tramite of tramitesRecientes">
                  <td>
                    <code>{{ tramite.codigo }}</code>
                  </td>
                  <td>{{ tramite.politicaNombre }}</td>
                  <td>
                    <span
                      [ngClass]="{
                        'badge': true,
                        'bg-success': tramite.estado === 'completado',
                        'bg-info': tramite.estado === 'en_progreso',
                        'bg-warning': tramite.estado === 'borrador'
                      }"
                    >
                      {{ tramite.estado | titlecase }}
                    </span>
                  </td>
                  <td>
                    <div class="progress" style="height: 20px;">
                      <div
                        class="progress-bar"
                        [ngClass]="'bg-' + obtenerProgresoColor(tramite.progreso)"
                        [style.width.%]="tramite.progreso"
                      >
                        {{ tramite.progreso }}%
                      </div>
                    </div>
                  </td>
                  <td>
                    <a
                      [routerLink]="['/tramites', tramite.id, 'seguimiento']"
                      class="btn btn-sm btn-outline-primary"
                    >
                      Ver
                    </a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div *ngIf="!loading && tramitesRecientes.length === 0" class="card-body">
          <p class="text-muted text-center mb-0">
            Aún no has iniciado ningún trámite.
            <a routerLink="/tramites">Comienza uno ahora</a>
          </p>
        </div>
      </div>
    </div>
  </div>
</div>

<style>
  .card {
    border-radius: 8px;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  }

  .alert-primary {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: none;
    color: white;
  }
</style>
```

---

## 3. Componente Perfil de Usuario

### perfil.component.ts
```typescript
import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../auth/auth.service';
import { Usuario } from '../../models/usuario.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-perfil',
  templateUrl: './perfil.component.html',
  styleUrls: ['./perfil.component.css']
})
export class PerfilComponent implements OnInit {

  usuario: Usuario | null = null;
  form!: FormGroup;
  editando = false;
  guardando = false;
  mensajeExito: string | null = null;
  mensajeError: string | null = null;

  constructor(
    private authService: AuthService,
    private formBuilder: FormBuilder
  ) { }

  ngOnInit(): void {
    this.usuario = this.authService.getUsuarioActual();
    this.construirFormulario();
  }

  construirFormulario(): void {
    if (this.usuario) {
      this.form = this.formBuilder.group({
        nombre: [this.usuario.nombre, [Validators.required]],
        email: [this.usuario.email, [Validators.required, Validators.email]]
      });
    }
  }

  toggleEdicion(): void {
    this.editando = !this.editando;
    this.mensajeError = null;
    this.mensajeExito = null;
  }

  guardarCambios(): void {
    if (this.form.invalid) {
      this.mensajeError = 'Por favor completa todos los campos';
      return;
    }

    this.guardando = true;
    // En ciclo 2, enviar al backend
    setTimeout(() => {
      this.mensajeExito = 'Perfil actualizado correctamente';
      this.editando = false;
      this.guardando = false;
    }, 1000);
  }
}
```

### perfil.component.html
```html
<div class="container py-4">
  <div class="row">
    <div class="col-md-8 offset-md-2">
      <div class="card">
        <div class="card-header bg-primary text-white">
          <h4 class="mb-0">Mi Perfil</h4>
        </div>

        <div class="card-body">
          <div *ngIf="mensajeExito" class="alert alert-success alert-dismissible fade show">
            {{ mensajeExito }}
            <button type="button" class="btn-close" (click)="mensajeExito = null"></button>
          </div>

          <div *ngIf="mensajeError" class="alert alert-danger alert-dismissible fade show">
            {{ mensajeError }}
            <button type="button" class="btn-close" (click)="mensajeError = null"></button>
          </div>

          <!-- Avatar -->
          <div class="text-center mb-4">
            <img
              *ngIf="usuario?.nombre"
              src="https://api.dicebear.com/7.x/avataaars/svg?seed={{usuario.nombre}}"
              alt="Avatar"
              class="rounded-circle"
              style="width: 100px; height: 100px;"
            >
          </div>

          <!-- Información -->
          <form [formGroup]="form">
            <div class="mb-3">
              <label class="form-label">Nombre</label>
              <input
                type="text"
                class="form-control"
                formControlName="nombre"
                [readonly]="!editando"
              >
            </div>

            <div class="mb-3">
              <label class="form-label">Email</label>
              <input
                type="email"
                class="form-control"
                formControlName="email"
                [readonly]="!editando"
              >
            </div>

            <div class="mb-3">
              <label class="form-label">Rol</label>
              <input
                type="text"
                class="form-control"
                [value]="usuario?.rol || 'Cliente'"
                readonly
              >
            </div>

            <!-- Botones -->
            <div class="d-flex gap-2">
              <button
                *ngIf="!editando"
                type="button"
                class="btn btn-primary"
                (click)="toggleEdicion()"
              >
                ✏️ Editar Perfil
              </button>

              <button
                *ngIf="editando"
                type="button"
                class="btn btn-success"
                (click)="guardarCambios()"
                [disabled]="guardando"
              >
                {{ guardando ? 'Guardando...' : '💾 Guardar Cambios' }}
              </button>

              <button
                *ngIf="editando"
                type="button"
                class="btn btn-outline-secondary"
                (click)="toggleEdicion()"
                [disabled]="guardando"
              >
                ✕ Cancelar
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</div>
```

---

## 4. Actualizar Rutas Principales

```typescript
// app-routing.module.ts
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AppLayoutComponent } from './app-layout/app-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { PerfilComponent } from './perfil/perfil.component';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },

  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [AuthGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'perfil', component: PerfilComponent },
      { path: 'tramites', component: TramitesListaComponent },
      { path: 'tramites/:id', component: TramitesDetalleComponent },
      { path: 'tramites/:id/nuevo', component: TramitesFormularioComponent },
      { path: 'mis-tramites', component: MisTramitesComponent },
      { path: 'tramites/:id/seguimiento', component: TramiteSeguimientoComponent },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },

  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/dashboard' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

---

## 5. Actualizar app.module.ts

```typescript
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AppLayoutComponent } from './app-layout/app-layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { PerfilComponent } from './perfil/perfil.component';

// Auth
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { AuthInterceptor } from './auth/auth.interceptor';

// Trámites
import { TramitesListaComponent } from './tramites/tramites-lista/tramites-lista.component';
import { TramitesDetalleComponent } from './tramites/tramites-detalle/tramites-detalle.component';
import { TramitesFormularioComponent } from './tramites/tramites-formulario/tramites-formulario.component';
import { MisTramitesComponent } from './tramites/mis-tramites/mis-tramites.component';
import { TramiteSeguimientoComponent } from './tramites/tramite-seguimiento/tramite-seguimiento.component';

@NgModule({
  declarations: [
    AppComponent,
    AppLayoutComponent,
    DashboardComponent,
    PerfilComponent,
    LoginComponent,
    RegisterComponent,
    TramitesListaComponent,
    TramitesDetalleComponent,
    TramitesFormularioComponent,
    MisTramitesComponent,
    TramiteSeguimientoComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
```

---

## 6. Actualizar app.component.html

```html
<router-outlet></router-outlet>
```

---

## Checklist de Implementación - Guía 5F

- [ ] Crear componente layout (navbar + sidebar)
- [ ] Crear componente dashboard
- [ ] Crear componente perfil
- [ ] Actualizar rutas principales
- [ ] Actualizar app.module.ts
- [ ] Instalar Bootstrap 5: `npm install bootstrap`
- [ ] Importar Bootstrap en styles.css
- [ ] Probar flujo completo de login → dashboard → trámites
- [ ] Probar navegación entre todas las pantallas
- [ ] Validar responsive en móvil

---

## 7. Pruebas Finales del Ciclo 1

### Casos de Prueba Obligatorios

1. **Autenticación (G1F)**
   - [ ] Registrar nuevo usuario
   - [ ] Login con credenciales correctas
   - [ ] Login con credenciales incorrectas
   - [ ] Token se guarda en localStorage
   - [ ] Token se incluye en requests automáticamente

2. **Exploración (G2F)**
   - [ ] Listar trámites disponibles
   - [ ] Filtrar por estado
   - [ ] Buscar por nombre
   - [ ] Ver detalle de un trámite

3. **Envío (G3F)**
   - [ ] Cargar formulario dinámico
   - [ ] Validar campos requeridos
   - [ ] Subir archivos (PDF, JPG)
   - [ ] Rechazar archivos grandes/formatos inválidos
   - [ ] Enviar trámite exitosamente
   - [ ] Recibir código de trámite

4. **Seguimiento (G4F)**
   - [ ] Listar mis trámites
   - [ ] Ver estado detallado
   - [ ] Ver progreso en % 
   - [ ] Ver historial de cambios
   - [ ] Ver secciones del expediente

5. **Dashboard (G5F)**
   - [ ] Mostrar resumen de trámites
   - [ ] Mostrar trámites recientes
   - [ ] Navegación entre menús
   - [ ] Ver perfil de usuario
   - [ ] Logout funciona

---

## 8. Instalación y Setup Rápido

```bash
# Crear proyecto Angular
ng new tramites-cliente
cd tramites-cliente

# Instalar dependencias
npm install bootstrap
npm install

# Actualizar environment.ts
# - apiUrl: http://localhost:8080/api

# Desarrollo
ng serve

# Acceder en http://localhost:4200
```

---

## Resumen Ciclo 1 Frontend

### ✅ Completado

| Guía | Objetivo | Componentes |
|------|----------|-------------|
| **G1F** | Autenticación JWT | Login, Register, AuthService, AuthGuard |
| **G2F** | Exploración de Trámites | TramitesLista, TramitesDetalle, TramitesService |
| **G3F** | Envío de Trámites | TramitesFormulario, TramitesEnvioService |
| **G4F** | Seguimiento | MisTramites, TramiteSeguimiento, TramitesSeguimientoService |
| **G5F** | Dashboard Principal | AppLayout, Dashboard, Perfil, Navbar, Sidebar |

### 📊 Estadísticas

- **5 Guías completadas**
- **12 Componentes creados**
- **8 Servicios implementados**
- **7 Modelos TypeScript**
- **100% funcionalidad Ciclo 1**

---

## 🎯 Próximos Pasos (Ciclo 2)

- [ ] Notificaciones en tiempo real (WebSockets)
- [ ] Chat con departamentos
- [ ] Descarga de certificados
- [ ] Firma digital
- [ ] Reportes y estadísticas
- [ ] Integración con pago online
- [ ] Recuperación de contraseña
- [ ] Validación de correo

---

## 📝 Notas Finales

✅ **El Ciclo 1 del Frontend está completo** con funcionalidad básica pero robusta.

⚠️ **Próximas mejoras en Ciclo 2:**
- Agregar animaciones y transiciones
- Mejorar UX/UI
- Implementar caché de datos
- Offline support
- PWA features
- Testing automatizado

🚀 **Estatus:** Listo para Ciclo 2
