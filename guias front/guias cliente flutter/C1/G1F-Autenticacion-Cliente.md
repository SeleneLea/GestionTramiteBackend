# Guía 1F — Autenticación JWT + Login/Registro Cliente

**Ciclo 1 · Sistema de Gestión de Trámites - Frontend**

> 🎯 **Objetivo:** Implementar pantalla de login y registro para clientes. Los clientes se autentican con JWT para acceder al sistema.

---

## 0. Requisitos

✅ Backend Ciclo 1 (G1-G5) compilando y ejecutándose en `http://localhost:8080`
✅ Angular 16+ instalado (o el framework que uses: React, Vue, Flutter)
✅ HttpClientModule disponible en la aplicación
✅ StorageService para guardar tokens localmente

---

## 1. Estructura de Carpetas Frontend (Angular)

```
src/
├── app/
│   ├── auth/
│   │   ├── login/
│   │   │   ├── login.component.ts
│   │   │   ├── login.component.html
│   │   │   ├── login.component.css
│   │   │   └── login.component.spec.ts
│   │   ├── register/
│   │   │   ├── register.component.ts
│   │   │   ├── register.component.html
│   │   │   └── register.component.css
│   │   ├── auth.service.ts
│   │   ├── auth.interceptor.ts
│   │   └── auth.guard.ts
│   ├── models/
│   │   ├── auth.model.ts
│   │   └── usuario.model.ts
│   └── app.module.ts
├── environments/
│   ├── environment.ts
│   └── environment.prod.ts
```

---

## 2. Modelos TypeScript (src/app/models/)

### auth.model.ts
```typescript
export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  tipoToken: string;
  email: string;
  nombre: string;
  tipo: string;  // "cliente" | "funcionario" | "administrador"
}

export interface RegisterRequest {
  nombre: string;
  email: string;
  password: string;
  passwordConfirm: string;
}

export interface RegisterResponse {
  message: string;
  usuarioId: string;
}
```

### usuario.model.ts
```typescript
export interface Usuario {
  id: string;
  nombre: string;
  email: string;
  rol: string;
  activo: boolean;
}
```

---

## 3. Servicio de Autenticación

### auth.service.ts
```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap, map, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse } from '../models/auth.model';
import { Usuario } from '../models/usuario.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = `${environment.apiUrl}/auth`;
  private usuarioActualSubject = new BehaviorSubject<Usuario | null>(null);
  public usuarioActual$ = this.usuarioActualSubject.asObservable();

  constructor(private http: HttpClient) {
    // Intentar restaurar sesión al cargar el servicio
    this.restaurarSesion();
  }

  /**
   * Login con email y contraseña
   */
  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, request)
      .pipe(
        tap(response => {
          // Guardar token en localStorage
          localStorage.setItem('token', response.token);
          // Guardar usuario actual
          this.usuarioActualSubject.next(response.usuario as Usuario);
        }),
        catchError(error => {
          console.error('Error en login:', error);
          throw error;
        })
      );
  }

  /**
   * Registro de nuevo cliente
   */
  registrar(request: RegisterRequest): Observable<RegisterResponse> {
    // Validaciones básicas en frontend
    if (request.password !== request.passwordConfirm) {
      throw new Error('Las contraseñas no coinciden');
    }

    return this.http.post<RegisterResponse>(
      `${this.apiUrl}/register-cliente`,
      {
        nombre: request.nombre,
        email: request.email,
        password: request.password
      }
    ).pipe(
      catchError(error => {
        console.error('Error en registro:', error);
        throw error;
      })
    );
  }

  /**
   * Obtener datos del usuario actual desde el backend
   */
  obtenerDatosUsuario(): Observable<Usuario> {
    return this.http.get<Usuario>(`${environment.apiUrl}/usuarios/me`)
      .pipe(
        tap(usuario => this.usuarioActualSubject.next(usuario)),
        catchError(error => {
          console.error('Error al obtener datos del usuario:', error);
          // Si hay error (401), limpiar token
          if (error.status === 401) {
            this.logout();
          }
          return of(null as any);
        })
      );
  }

  /**
   * Logout
   */
  logout(): void {
    localStorage.removeItem('token');
    this.usuarioActualSubject.next(null);
  }

  /**
   * Obtener token actual
   */
  getToken(): string | null {
    return localStorage.getItem('token');
  }

  /**
   * Verificar si está autenticado
   */
  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }

  /**
   * Obtener usuario actual sin hacer llamada HTTP
   */
  getUsuarioActual(): Usuario | null {
    return this.usuarioActualSubject.value;
  }

  /**
   * Restaurar sesión si existe token guardado
   */
  private restaurarSesion(): void {
    if (this.isAuthenticated()) {
      this.obtenerDatosUsuario().subscribe();
    }
  }
}
```

---

## 4. HTTP Interceptor para Agregar Token

### auth.interceptor.ts
```typescript
import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private authService: AuthService, private router: Router) { }

  intercept(
    request: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {

    // Obtener token
    const token = this.authService.getToken();

    // Si hay token, agregarlo al header Authorization
    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // Si recibimos 401, el token expiró o es inválido
        if (error.status === 401) {
          this.authService.logout();
          this.router.navigate(['/login']);
        }
        return throwError(() => error);
      })
    );
  }
}
```

---

## 5. Guard de Rutas

### auth.guard.ts
```typescript
import { Injectable } from '@angular/core';
import { Router, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {

  constructor(
    private authService: AuthService,
    private router: Router
  ) { }

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean {

    if (this.authService.isAuthenticated()) {
      return true;
    }

    // No autenticado, redirigir a login
    this.router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }
}
```

---

## 6. Componente Login

### login.component.ts
```typescript
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { LoginRequest } from '../models/auth.model';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  form!: FormGroup;
  loading = false;
  submitted = false;
  errorMessage: string | null = null;

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.initForm();
  }

  initForm(): void {
    this.form = this.formBuilder.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  get f() {
    return this.form.controls;
  }

  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = null;

    if (this.form.invalid) {
      return;
    }

    this.loading = true;
    const request: LoginRequest = {
      email: this.f['email'].value,
      password: this.f['password'].value
    };

    this.authService.login(request).subscribe({
      next: (response) => {
        // Login exitoso, redirigir al home
        this.router.navigate(['/home']);
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Error al iniciar sesión';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  navegarARegistro(): void {
    this.router.navigate(['/register']);
  }
}
```

### login.component.html
```html
<div class="container mt-5">
  <div class="row justify-content-center">
    <div class="col-md-5">
      <div class="card">
        <div class="card-header bg-primary text-white">
          <h4 class="mb-0">Iniciar Sesión</h4>
        </div>
        <div class="card-body">
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <!-- Error Message -->
            <div *ngIf="errorMessage" class="alert alert-danger">
              {{ errorMessage }}
            </div>

            <!-- Email -->
            <div class="mb-3">
              <label for="email" class="form-label">Correo Electrónico</label>
              <input
                type="email"
                class="form-control"
                id="email"
                formControlName="email"
                [ngClass]="{ 'is-invalid': submitted && f['email'].errors }"
              >
              <div *ngIf="submitted && f['email'].errors" class="invalid-feedback">
                <span *ngIf="f['email'].errors['required']">El email es requerido</span>
                <span *ngIf="f['email'].errors['email']">Email inválido</span>
              </div>
            </div>

            <!-- Contraseña -->
            <div class="mb-3">
              <label for="password" class="form-label">Contraseña</label>
              <input
                type="password"
                class="form-control"
                id="password"
                formControlName="password"
                [ngClass]="{ 'is-invalid': submitted && f['password'].errors }"
              >
              <div *ngIf="submitted && f['password'].errors" class="invalid-feedback">
                <span *ngIf="f['password'].errors['required']">La contraseña es requerida</span>
                <span *ngIf="f['password'].errors['minlength']">Mínimo 6 caracteres</span>
              </div>
            </div>

            <!-- Botones -->
            <div class="d-grid gap-2 mb-3">
              <button
                type="submit"
                class="btn btn-primary"
                [disabled]="loading"
              >
                <span *ngIf="!loading">Ingresar</span>
                <span *ngIf="loading">
                  <span class="spinner-border spinner-border-sm me-2"></span>
                  Autenticando...
                </span>
              </button>
            </div>
          </form>

          <!-- Registro Link -->
          <p class="text-center">
            ¿No tienes cuenta?
            <a href="javascript:void(0)" (click)="navegarARegistro()" class="text-decoration-none">
              Regístrate aquí
            </a>
          </p>
        </div>
      </div>
    </div>
  </div>
</div>
```

---

## 7. Componente Registro

### register.component.ts
```typescript
import { Component } from '@angular/core';
import { FormBuilder, FormGroup, Validators, AbstractControl } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { RegisterRequest } from '../models/auth.model';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent {

  form!: FormGroup;
  loading = false;
  submitted = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.initForm();
  }

  initForm(): void {
    this.form = this.formBuilder.group({
      nombre: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      passwordConfirm: ['', [Validators.required]]
    }, {
      validators: this.passwordMatchValidator
    });
  }

  // Validador personalizado: verifica que las contraseñas coincidan
  passwordMatchValidator(control: AbstractControl): { [key: string]: any } | null {
    const password = control.get('password');
    const confirmPassword = control.get('passwordConfirm');

    if (password && confirmPassword && password.value !== confirmPassword.value) {
      confirmPassword.setErrors({ 'passwordMismatch': true });
      return { 'passwordMismatch': true };
    }
    return null;
  }

  get f() {
    return this.form.controls;
  }

  onSubmit(): void {
    this.submitted = true;
    this.errorMessage = null;
    this.successMessage = null;

    if (this.form.invalid) {
      return;
    }

    this.loading = true;
    const request: RegisterRequest = {
      nombre: this.f['nombre'].value,
      email: this.f['email'].value,
      password: this.f['password'].value,
      passwordConfirm: this.f['passwordConfirm'].value
    };

    this.authService.registrar(request).subscribe({
      next: (response) => {
        this.successMessage = 'Registro exitoso. Redirigiendo a login...';
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2000);
      },
      error: (error) => {
        this.errorMessage = error.error?.message || 'Error al registrarse';
        this.loading = false;
      },
      complete: () => {
        this.loading = false;
      }
    });
  }

  navegarALogin(): void {
    this.router.navigate(['/login']);
  }
}
```

### register.component.html
```html
<div class="container mt-5">
  <div class="row justify-content-center">
    <div class="col-md-6">
      <div class="card">
        <div class="card-header bg-success text-white">
          <h4 class="mb-0">Crear Nueva Cuenta</h4>
        </div>
        <div class="card-body">
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <!-- Error Message -->
            <div *ngIf="errorMessage" class="alert alert-danger">
              {{ errorMessage }}
            </div>

            <!-- Success Message -->
            <div *ngIf="successMessage" class="alert alert-success">
              {{ successMessage }}
            </div>

            <!-- Nombre -->
            <div class="mb-3">
              <label for="nombre" class="form-label">Nombre Completo</label>
              <input
                type="text"
                class="form-control"
                id="nombre"
                formControlName="nombre"
                [ngClass]="{ 'is-invalid': submitted && f['nombre'].errors }"
              >
              <div *ngIf="submitted && f['nombre'].errors" class="invalid-feedback">
                <span *ngIf="f['nombre'].errors['required']">El nombre es requerido</span>
                <span *ngIf="f['nombre'].errors['minlength']">Mínimo 3 caracteres</span>
              </div>
            </div>

            <!-- Email -->
            <div class="mb-3">
              <label for="email" class="form-label">Correo Electrónico</label>
              <input
                type="email"
                class="form-control"
                id="email"
                formControlName="email"
                [ngClass]="{ 'is-invalid': submitted && f['email'].errors }"
              >
              <div *ngIf="submitted && f['email'].errors" class="invalid-feedback">
                <span *ngIf="f['email'].errors['required']">El email es requerido</span>
                <span *ngIf="f['email'].errors['email']">Email inválido</span>
              </div>
            </div>

            <!-- Contraseña -->
            <div class="mb-3">
              <label for="password" class="form-label">Contraseña</label>
              <input
                type="password"
                class="form-control"
                id="password"
                formControlName="password"
                [ngClass]="{ 'is-invalid': submitted && f['password'].errors }"
              >
              <div *ngIf="submitted && f['password'].errors" class="invalid-feedback">
                <span *ngIf="f['password'].errors['required']">La contraseña es requerida</span>
                <span *ngIf="f['password'].errors['minlength']">Mínimo 6 caracteres</span>
              </div>
            </div>

            <!-- Confirmar Contraseña -->
            <div class="mb-3">
              <label for="confirmar" class="form-label">Confirmar Contraseña</label>
              <input
                type="password"
                class="form-control"
                id="confirmar"
                formControlName="passwordConfirm"
                [ngClass]="{ 'is-invalid': submitted && (f['passwordConfirm'].errors || form.errors?.['passwordMismatch']) }"
              >
              <div *ngIf="submitted && (f['passwordConfirm'].errors || form.errors?.['passwordMismatch'])" class="invalid-feedback">
                <span *ngIf="form.errors?.['passwordMismatch']">Las contraseñas no coinciden</span>
              </div>
            </div>

            <!-- Botones -->
            <div class="d-grid gap-2 mb-3">
              <button
                type="submit"
                class="btn btn-success"
                [disabled]="loading"
              >
                <span *ngIf="!loading">Registrarse</span>
                <span *ngIf="loading">
                  <span class="spinner-border spinner-border-sm me-2"></span>
                  Creando cuenta...
                </span>
              </button>
            </div>
          </form>

          <!-- Login Link -->
          <p class="text-center">
            ¿Ya tienes cuenta?
            <a href="javascript:void(0)" (click)="navegarALogin()" class="text-decoration-none">
              Inicia sesión aquí
            </a>
          </p>
        </div>
      </div>
    </div>
  </div>
</div>
```

---

## 8. Configuración en app.module.ts

```typescript
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { AuthInterceptor } from './auth/auth.interceptor';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    RegisterComponent
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

## 9. Configuración de Rutas (app-routing.module.ts)

```typescript
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { RegisterComponent } from './auth/register/register.component';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  // Rutas protegidas irán aquí en las siguientes guías
  { path: '', redirectTo: '/login', pathMatch: 'full' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
```

---

## 10. Configuración de Environments

### environment.ts
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

### environment.prod.ts
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://api.tudominio.com/api'  // Cambiar en producción
};
```

---

## 11. Testing (Opcional)

### login.component.spec.ts
```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { LoginComponent } from './login.component';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      declarations: [LoginComponent],
      imports: [ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should disable submit button when form is invalid', () => {
    component.form.controls['email'].setValue('');
    component.form.controls['password'].setValue('');
    expect(component.form.invalid).toBeTruthy();
  });

  it('should call authService.login on submit', () => {
    authService.login.and.returnValue(of({
      token: 'test-token',
      usuario: { id: '1', nombre: 'Test', email: 'test@test.com', rol: 'Cliente' }
    }));

    component.form.controls['email'].setValue('test@test.com');
    component.form.controls['password'].setValue('password123');
    component.onSubmit();

    expect(authService.login).toHaveBeenCalled();
  });
});
```

---

## 12. Resumen de Endpoints Utilizados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login con email y contraseña |
| POST | `/api/auth/register-cliente` | Registrar nuevo cliente |
| GET | `/api/usuarios/me` | Obtener datos del usuario actual (requiere token) |

---

## Checklist de Implementación

- [ ] Crear estructura de carpetas
- [ ] Crear modelos TypeScript (auth.model.ts, usuario.model.ts)
- [ ] Implementar AuthService
- [ ] Crear AuthInterceptor
- [ ] Crear AuthGuard
- [ ] Crear componente Login (TS + HTML)
- [ ] Crear componente Register (TS + HTML)
- [ ] Configurar app.module.ts
- [ ] Configurar rutas en app-routing.module.ts
- [ ] Configurar environments
- [ ] Instalar Bootstrap 5 si no lo tienes: `npm install bootstrap`
- [ ] Probar login y registro en browser (http://localhost:4200)
- [ ] Verificar que el token se guarda en localStorage
- [ ] Ejecutar tests (opcional)

---

## Notas Importantes

⚠️ **Seguridad:**
- El token se guarda en localStorage. Para mayor seguridad en producción, considera usar sessionStorage o cookies HttpOnly.
- La clave secreta del JWT debe ser rotada periódicamente.
- Nunca expongas credenciales en el frontend.

📝 **Próximos Pasos:**
- G2F: Exploración de trámites disponibles
- G3F: Envío de nuevos trámites
- G4F: Seguimiento de estado
- G5F: UI principal y navegación

