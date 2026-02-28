# GUIA_FIREBASE

## 1) Crear proyecto Firebase
1. Entra a Firebase Console y crea un proyecto.
2. Agrega app Android con package `com.carevalojesus.pokeapi`.
3. Descarga `google-services.json` y colocalo en `app/google-services.json`.

## 2) Habilitar autenticacion
En Firebase Console > Authentication > Sign-in method:
1. Activa `Email/Password` (entrenador y admin).
2. `Anonymous` es opcional y no se usa en el flujo actual.

## 3) Crear usuario admin
En Authentication > Users:
1. Crea un usuario con email y password.
2. Copia su UID.

Luego, en Firestore, crea el documento:
- Coleccion: `trainers`
- Documento: `<UID_ADMIN>`
- Campos:
  - `uid`: string
  - `email`: string
  - `displayName`: string
  - `role`: `admin`

Nota: la app valida que `role == "admin"` para permitir acceso al panel admin.

## 4) Habilitar Firestore
1. Crea base Firestore en modo Native.
2. Usa estas colecciones:
- `trainers/{uid}`
- `trainers/{uid}/inventory/{pokemonId}`
- `campaigns/{campaignId}`
- `campaigns/{campaignId}/claims/{uid}`

## 5) Reglas de seguridad sugeridas
Usa estas reglas iniciales (ajustalas en produccion):

```txt
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() {
      return request.auth != null;
    }

    function isAdmin() {
      return signedIn() &&
        get(/databases/$(database)/documents/trainers/$(request.auth.uid)).data.role == 'admin';
    }

    match /trainers/{uid} {
      allow read: if signedIn() && (request.auth.uid == uid || isAdmin());
      allow create, update: if signedIn() && (request.auth.uid == uid || isAdmin());
      allow delete: if false;

      match /inventory/{pokemonId} {
        allow read: if signedIn() && (request.auth.uid == uid || isAdmin());
        allow create, update: if signedIn() && request.auth.uid == uid;
        allow delete: if false;
      }

      match /notifications/{notificationId} {
        allow read: if signedIn() && (request.auth.uid == uid || isAdmin());
        allow create: if signedIn() && isAdmin();
        allow update: if signedIn() && (request.auth.uid == uid || isAdmin());
        allow delete: if signedIn() && isAdmin();
      }
    }

    match /campaigns/{campaignId} {
      allow read: if signedIn();
      allow create, update, delete: if isAdmin();

      match /claims/{uid} {
        allow read: if signedIn() && (request.auth.uid == uid || isAdmin());
        allow create: if signedIn() && request.auth.uid == uid;
        allow update, delete: if false;
      }
    }
  }
}
```

## 6) Flujo funcional en la app
1. Pantalla inicial:
- `Entrar como entrenador` -> login anonimo.
- `Entrar como admin` -> email/password + rol admin en Firestore.
2. Admin:
- Crea campana QR (3 Pokemon aleatorios).
- Ve entrenadores registrados e inventario cloud.
3. Entrenador:
- Escanea QR de recompensa.
- Si no canjeo antes, desbloquea 3 Pokemon aleatorios.
- Si ya canjeo, aparece mensaje de canje repetido.

## 7) Observaciones importantes
1. Si compilar falla, revisa que exista `app/google-services.json`.
2. En este entorno no se pudo compilar por `JAVA_HOME` no configurado.
3. Para mas seguridad (anti-manipulacion), migra la logica de canje/aleatoriedad a Cloud Functions.
