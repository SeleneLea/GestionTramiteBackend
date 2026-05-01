# Guía 1W — Autenticación JWT · Login Web (Admin / Funcionario)

**Ciclo 1 · Sistema de Gestión de Trámites — Frontend Web**

> **Objetivo:** Implementar login con JWT para Administradores y Funcionarios en Angular. El cliente usa Flutter; la web es exclusiva para roles internos.

---

## 0. Requisitos

- Backend C1 corriendo en `http://localhost:8080`
- Node.js 18+ y Angular CLI: `npm install -g @angular/cli`
- Crear proyecto: `ng new tramites-web --routing --style=css` → `cd tramites-web`
- Bootstrap 5: `npm install bootstrap` → agregar en `angular.json` bajo `styles`

---

## 1. Credenciales de Prueba

| Rol | Email | Password |
|-----|-------|----------|
| Administrador | `admin@cre.bo` | `admin12345` |
| SuperUser | `superuser@cre.bo` | `super12345` |
| Funcionario | `funcionario@cre.bo` | `func12345` |

> Los clientes (`cliente@cre.bo`) usan la app Flutter, **no** la web.

---

## 2. Estructura de Carpetas

```
src/app/
├── core/
│   ├── models/
│   │   ├── auth.model.ts
│   │   └── usuario.model.ts
│   ├── services/
│   │   └── auth.service.ts
│   ├── interceptors/
│   │   └── auth.interceptor.ts
│   └── guards/
│       ├── auth.guard.ts
│       └── rol.guard.ts
├── auth/
│   └── login/
│       ├── login.component.ts
│       └── login.component.html
└── shared/
    └── navbar/
        ├── navbar.component.ts
        └── navbar.component.html
```

---

## 3. Modelos

### src/app/core/models/auth.model.ts
```typescript
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tipo: string;
  email: string;
  nombre: string;
  rol: string;
  userId: string;
}
```

### src/app/core/models/usuario.model.ts
```typescript
export interface Usuario {
  id: string;
  nombre: string;
  apellido: string;
  email: string;
  tipo: string;       // "administrador" | "funcionario" | "cliente"
  rolId: string;
  rol?: string;       // nombre del rol, ej: "Administrador"
  activo: boolean;
  departamentosIds?: string[];
  fechaRegistro?: string;
}
```

---

## 4. Environment

### src/environments/environment.ts
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

---

## 5. AuthService

### src/app/core/services/auth.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { LoginRequest, LoginResponse } from '../models/auth.model';
import { Usuario } from '../models/usuario.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private apiUrl = `${environment.apiUrl}/auth`;
  private _usuario = new BehaviorSubject<LoginResponse | null>(null);
  usuario$ = this._usuario.asObservable();

  constructor(private http: HttpClient, private router: Router) {
    const saved = localStorage.getItem('auth');
    if (saved) this._usuario.next(JSON.parse(saved));
  }

  login(req: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, req).pipe(
      tap(res => {
        localStorage.setItem('auth', JSON.stringify(res));
        localStorage.setItem('token', res.token);
        this._usuario.next(res);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('auth');
    localStorage.removeItem('token');
    this._usuario.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getUsuario(): LoginResponse | null {
    return this._usuario.value;
  }

  getRol(): string {
    return this._usuario.value?.rol ?? '';
  }

  isAdmin(): boolean {
    return ['Administrador', 'SuperUser'].includes(this.getRol());
  }

  isFuncionario(): boolean {
    return this.getRol() === 'Funcionario';
  }

  // Obtener perfil completo desde el backend
  obtenerPerfil(): Observable<Usuario> {
    return this.http.get<Usuario>(`${environment.apiUrl}/usuarios/me`);
  }
}
```

---

## 6. Interceptor JWT

### src/app/core/interceptors/auth.interceptor.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(private auth: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.auth.getToken();
    const authReq = token
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

    return next.handle(authReq).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401) this.auth.logout();
        return throwError(() => err);
      })
    );
  }
}
```

---

## 7. Guards

### src/app/core/guards/auth.guard.ts
```typescript
import { Injectable } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  constructor(private auth: AuthService, private router: Router) {}

  canActivate(): boolean {
    if (this.auth.isAuthenticated()) return true;
    this.router.navigate(['/login']);
    return false;
  }
}
```

### src/app/core/guards/rol.guard.ts
```typescript
import { Injectable } from '@angular/core';
import { CanActivate, ActivatedRouteSnapshot, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Injectable({ providedIn: 'root' })
export class RolGuard implements CanActivate {
  constructor(private auth: AuthService, private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const rolesPermitidos: string[] = route.data['roles'] ?? [];
    const rolUsuario = this.auth.getRol();

    if (rolesPermitidos.length === 0 || rolesPermitidos.includes(rolUsuario)) {
      return true;
    }
    this.router.navigate(['/no-autorizado']);
    return false;
  }
}
```

---

## 8. Componente Login

### login.component.ts
```typescript
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html'
})
export class LoginComponent {
  form: FormGroup;
  loading = false;
  error = '';

  constructor(private fb: FormBuilder, private auth: AuthService, private router: Router) {
    this.form = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });

    // Si ya está autenticado, redirigir
    if (this.auth.isAuthenticated()) {
      this.redirigirSegunRol();
    }
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';

    this.auth.login(this.form.value).subscribe({
      next: () => this.redirigirSegunRol(),
      error: err => {
        this.error = err.error?.message ?? 'Credenciales incorrectas';
        this.loading = false;
      }
    });
  }

  private redirigirSegunRol(): void {
    if (this.auth.isAdmin()) this.router.navigate(['/admin/dashboard']);
    else if (this.auth.isFuncionario()) this.router.navigate(['/funcionario/tramites']);
    else this.router.navigate(['/login']);
  }
}
```

### login.component.html
```html
<div class="min-vh-100 d-flex align-items-center bg-light">
  <div class="container">
    <div class="row justify-content-center">
      <div class="col-md-4">

        <div class="text-center mb-4">
          <h2 class="fw-bold text-primary">CRE · Gestión de Trámites</h2>
          <p class="text-muted">Portal interno para Administradores y Funcionarios</p>
        </div>

        <div class="card shadow-sm">
          <div class="card-body p-4">
            <h5 class="card-title mb-4">Iniciar Sesión</h5>

            <div *ngIf="error" class="alert alert-danger">{{ error }}</div>

            <form [formGroup]="form" (ngSubmit)="submit()">
              <div class="mb-3">
                <label class="form-label">Correo Electrónico</label>
                <input type="email" class="form-control" formControlName="email"
                       [class.is-invalid]="form.get('email')?.touched && form.get('email')?.invalid">
                <div class="invalid-feedback">Email requerido o inválido</div>
              </div>

              <div class="mb-3">
                <label class="form-label">Contraseña</label>
                <input type="password" class="form-control" formControlName="password"
                       [class.is-invalid]="form.get('password')?.touched && form.get('password')?.invalid">
                <div class="invalid-feedback">Contraseña requerida (mín. 6 caracteres)</div>
              </div>

              <button type="submit" class="btn btn-primary w-100" [disabled]="loading">
                <span *ngIf="loading" class="spinner-border spinner-border-sm me-2"></span>
                {{ loading ? 'Autenticando...' : 'Ingresar' }}
              </button>
            </form>
          </div>
        </div>

        <!-- Credenciales de demo -->
        <div class="card mt-3 border-info">
          <div class="card-body p-3 small">
            <strong>Demo:</strong><br>
            Admin: <code>admin@cre.bo</code> / <code>admin12345</code><br>
            Funcionario: <code>funcionario@cre.bo</code> / <code>func12345</code>
          </div>
        </div>

      </div>
    </div>
  </div>
</div>
```

---

## 9. Navbar Compartida

### src/app/shared/navbar/navbar.component.ts
```typescript
import { Component } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-navbar',
  templateUrl: './navbar.component.html'
})
export class NavbarComponent {
  constructor(public auth: AuthService) {}
}
```

### navbar.component.html
```html
<nav class="navbar navbar-expand-lg navbar-dark bg-primary">
  <div class="container-fluid">
    <span class="navbar-brand fw-bold">CRE · Gestión de Trámites</span>

    <div class="navbar-nav ms-auto d-flex flex-row align-items-center gap-3">
      <!-- Admin -->
      <ng-container *ngIf="auth.isAdmin()">
        <a class="nav-link text-white" routerLink="/admin/usuarios">Usuarios</a>
        <a class="nav-link text-white" routerLink="/admin/departamentos">Departamentos</a>
        <a class="nav-link text-white" routerLink="/admin/politicas">Políticas</a>
        <a class="nav-link text-white" routerLink="/admin/diagramas">Diagramas</a>
      </ng-container>

      <!-- Funcionario -->
      <ng-container *ngIf="auth.isFuncionario()">
        <a class="nav-link text-white" routerLink="/funcionario/tramites">Mis Trámites</a>
      </ng-container>

      <!-- Usuario activo -->
      <span class="text-white-50 small">
        {{ auth.getUsuario()?.nombre }} ({{ auth.getRol() }})
      </span>

      <button class="btn btn-outline-light btn-sm" (click)="auth.logout()">Salir</button>
    </div>
  </div>
</nav>
```

---

## 10. app.module.ts

```typescript
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { CommonModule } from '@angular/common';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './auth/login/login.component';
import { NavbarComponent } from './shared/navbar/navbar.component';
import { AuthInterceptor } from './core/interceptors/auth.interceptor';

@NgModule({
  declarations: [AppComponent, LoginComponent, NavbarComponent],
  imports: [BrowserModule, AppRoutingModule, ReactiveFormsModule, HttpClientModule, CommonModule],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
```

---

## 11. app-routing.module.ts (esqueleto completo)

```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { AuthGuard } from './core/guards/auth.guard';
import { RolGuard } from './core/guards/rol.guard';

const routes: Routes = [
  { path: 'login', component: LoginComponent },

  // Rutas Admin (ver G2W, G3W, G5W)
  {
    path: 'admin',
    canActivate: [AuthGuard, RolGuard],
    data: { roles: ['Administrador', 'SuperUser'] },
    children: [
      { path: 'dashboard', loadChildren: () => import('./admin/admin.module').then(m => m.AdminModule) },
      { path: 'usuarios',     loadChildren: () => import('./admin/usuarios/usuarios.module').then(m => m.UsuariosModule) },
      { path: 'departamentos',loadChildren: () => import('./admin/departamentos/deptos.module').then(m => m.DeptosModule) },
      { path: 'politicas',    loadChildren: () => import('./admin/politicas/politicas.module').then(m => m.PoliticasModule) },
      { path: 'diagramas',    loadChildren: () => import('./admin/diagramas/diagramas.module').then(m => m.DiagramasModule) },
    ]
  },

  // Rutas Funcionario (ver G4W)
  {
    path: 'funcionario',
    canActivate: [AuthGuard, RolGuard],
    data: { roles: ['Funcionario'] },
    children: [
      { path: 'tramites', loadChildren: () => import('./funcionario/funcionario.module').then(m => m.FuncionarioModule) },
    ]
  },

  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: '**', redirectTo: '/login' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
```

---

## 12. Endpoints utilizados en esta guía

| Método | Endpoint | Rol |
|--------|----------|-----|
| POST | `/api/auth/login` | Todos |
| GET | `/api/usuarios/me` | Autenticado |

---

## Checklist G1W

- [ ] Crear proyecto Angular con routing y Bootstrap
- [ ] Crear `environment.ts` con `apiUrl: 'http://localhost:8080/api'`
- [ ] Implementar `AuthService` con `login()` y `logout()`
- [ ] Implementar `AuthInterceptor` para adjuntar token JWT
- [ ] Implementar `AuthGuard` y `RolGuard`
- [ ] Crear `LoginComponent` con formulario reactivo
- [ ] Crear `NavbarComponent` compartida
- [ ] Probar login con `admin@cre.bo` → redirige a `/admin/dashboard`
- [ ] Probar login con `funcionario@cre.bo` → redirige a `/funcionario/tramites`
- [ ] Verificar que rutas protegidas redirigen a `/login` sin token

**Próximo:** G2W — Gestión de Usuarios y Departamentos (Admin)
