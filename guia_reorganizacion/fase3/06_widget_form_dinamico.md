# Fase 3.6 · Widget FormDinamico

> Formulario que se construye a partir de una **definición de campos**. Lo usa principalmente la corrección de secciones de expediente.

---

## 1. Objetivo

Que cualquier pantalla que necesite un formulario configurable lo describa como datos (`List<CampoDef>`) en lugar de armarlo a mano con widgets `TextField`/`DropdownButton`/etc.

---

## 2. Archivos

- `lib/widgets/form_dinamico_types.dart`
- `lib/widgets/form_dinamico.dart`

---

## 3. Tipos públicos

### `lib/widgets/form_dinamico_types.dart`

```dart
enum TipoCampo { texto, textoLargo, numero, fecha, select, booleano }

class OpcionSelect {
  final String valor;
  final String label;
  const OpcionSelect({required this.valor, required this.label});
}

class CampoDef {
  final String key;
  final String label;
  final TipoCampo tipo;
  final String? ayuda;
  final String? placeholder;
  final bool requerido;
  final bool soloLectura;
  final List<OpcionSelect>? opciones;
  final num? min;
  final num? max;
  final int? minLength;
  final int? maxLength;
  final dynamic valorInicial;

  const CampoDef({
    required this.key,
    required this.label,
    required this.tipo,
    this.ayuda,
    this.placeholder,
    this.requerido = false,
    this.soloLectura = false,
    this.opciones,
    this.min,
    this.max,
    this.minLength,
    this.maxLength,
    this.valorInicial,
  });
}
```

---

## 4. Widget

### `lib/widgets/form_dinamico.dart`

```dart
import 'package:flutter/material.dart';
import 'form_dinamico_types.dart';

class FormDinamico extends StatefulWidget {
  final List<CampoDef> campos;
  final Map<String, dynamic> valoresIniciales;
  final String textoBoton;
  final bool mostrarBoton;
  final void Function(Map<String, dynamic>)? onCambio;
  final void Function(Map<String, dynamic>) onEnviar;

  const FormDinamico({
    super.key,
    required this.campos,
    this.valoresIniciales = const {},
    this.textoBoton = 'Guardar',
    this.mostrarBoton = true,
    this.onCambio,
    required this.onEnviar,
  });

  @override
  State<FormDinamico> createState() => _FormDinamicoState();
}

class _FormDinamicoState extends State<FormDinamico> {
  final _formKey = GlobalKey<FormState>();
  late Map<String, dynamic> _valores;

  @override
  void initState() {
    super.initState();
    _valores = {
      for (final c in widget.campos)
        c.key: widget.valoresIniciales[c.key] ?? c.valorInicial ?? _defaultPorTipo(c.tipo),
    };
  }

  dynamic _defaultPorTipo(TipoCampo t) =>
      t == TipoCampo.booleano ? false : '';

  String? _validar(CampoDef c, dynamic valor) {
    final s = valor?.toString() ?? '';
    if (c.requerido && s.trim().isEmpty) return 'Este campo es obligatorio';
    if (c.minLength != null && s.length < c.minLength!) {
      return 'Mínimo ${c.minLength} caracteres';
    }
    if (c.maxLength != null && s.length > c.maxLength!) {
      return 'Máximo ${c.maxLength} caracteres';
    }
    if (c.tipo == TipoCampo.numero && s.isNotEmpty) {
      final n = num.tryParse(s);
      if (n == null) return 'Debe ser un número';
      if (c.min != null && n < c.min!) return 'Mínimo ${c.min}';
      if (c.max != null && n > c.max!) return 'Máximo ${c.max}';
    }
    return null;
  }

  void _setValor(String key, dynamic valor) {
    setState(() => _valores[key] = valor);
    widget.onCambio?.call(Map.from(_valores));
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) return;
    widget.onEnviar(Map.from(_valores));
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final c in widget.campos) _buildCampo(c),
          if (widget.mostrarBoton) ...[
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _submit,
              child: Text(widget.textoBoton),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildCampo(CampoDef c) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(c.label, style: const TextStyle(fontWeight: FontWeight.w600)),
              if (c.requerido)
                const Text(' *', style: TextStyle(color: Colors.red)),
            ],
          ),
          const SizedBox(height: 4),
          _buildControl(c),
          if (c.ayuda != null) ...[
            const SizedBox(height: 4),
            Text(c.ayuda!,
                style: TextStyle(fontSize: 12, color: Colors.grey[600])),
          ],
        ],
      ),
    );
  }

  Widget _buildControl(CampoDef c) {
    switch (c.tipo) {
      case TipoCampo.texto:
        return TextFormField(
          initialValue: _valores[c.key]?.toString(),
          enabled: !c.soloLectura,
          decoration: InputDecoration(
            border: const OutlineInputBorder(),
            hintText: c.placeholder,
          ),
          validator: (v) => _validar(c, v),
          onChanged: (v) => _setValor(c.key, v),
        );

      case TipoCampo.textoLargo:
        return TextFormField(
          initialValue: _valores[c.key]?.toString(),
          enabled: !c.soloLectura,
          maxLines: 4,
          decoration: InputDecoration(
            border: const OutlineInputBorder(),
            hintText: c.placeholder,
          ),
          validator: (v) => _validar(c, v),
          onChanged: (v) => _setValor(c.key, v),
        );

      case TipoCampo.numero:
        return TextFormField(
          initialValue: _valores[c.key]?.toString(),
          enabled: !c.soloLectura,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: const InputDecoration(border: OutlineInputBorder()),
          validator: (v) => _validar(c, v),
          onChanged: (v) => _setValor(c.key, num.tryParse(v) ?? v),
        );

      case TipoCampo.fecha:
        return _DatePickerField(
          valor: _valores[c.key],
          enabled: !c.soloLectura,
          onChanged: (v) => _setValor(c.key, v),
          validator: (v) => _validar(c, v),
        );

      case TipoCampo.select:
        return DropdownButtonFormField<String>(
          value: _valores[c.key]?.toString().isNotEmpty == true
              ? _valores[c.key].toString()
              : null,
          items: (c.opciones ?? [])
              .map((op) => DropdownMenuItem(value: op.valor, child: Text(op.label)))
              .toList(),
          onChanged: c.soloLectura ? null : (v) => _setValor(c.key, v),
          decoration: const InputDecoration(border: OutlineInputBorder()),
          validator: (v) => _validar(c, v),
        );

      case TipoCampo.booleano:
        return SwitchListTile(
          contentPadding: EdgeInsets.zero,
          value: _valores[c.key] == true,
          onChanged: c.soloLectura ? null : (v) => _setValor(c.key, v),
          title: Text(c.placeholder ?? ''),
        );
    }
  }
}

class _DatePickerField extends StatelessWidget {
  final dynamic valor;
  final bool enabled;
  final void Function(String) onChanged;
  final String? Function(String?)? validator;

  const _DatePickerField({
    required this.valor,
    required this.enabled,
    required this.onChanged,
    this.validator,
  });

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      readOnly: true,
      enabled: enabled,
      controller: TextEditingController(text: valor?.toString() ?? ''),
      decoration: const InputDecoration(
        border: OutlineInputBorder(),
        suffixIcon: Icon(Icons.calendar_today),
      ),
      validator: validator,
      onTap: enabled ? () async {
        final picked = await showDatePicker(
          context: context,
          initialDate: DateTime.tryParse(valor?.toString() ?? '') ?? DateTime.now(),
          firstDate: DateTime(2000),
          lastDate: DateTime(2100),
        );
        if (picked != null) {
          onChanged(picked.toIso8601String().substring(0, 10));
        }
      } : null,
    );
  }
}
```

---

## 5. Uso

### En `realizar_correccion_screen.dart` o expediente:

```dart
import '../../widgets/form_dinamico.dart';
import '../../widgets/form_dinamico_types.dart';

final campos = [
  CampoDef(
    key: 'observaciones',
    label: 'Observaciones',
    tipo: TipoCampo.textoLargo,
    requerido: true,
    minLength: 10,
  ),
  CampoDef(
    key: 'monto',
    label: 'Monto (Bs.)',
    tipo: TipoCampo.numero,
    min: 0,
  ),
  CampoDef(
    key: 'fechaInspeccion',
    label: 'Fecha de inspección',
    tipo: TipoCampo.fecha,
    requerido: true,
  ),
  CampoDef(
    key: 'resultado',
    label: 'Resultado',
    tipo: TipoCampo.select,
    requerido: true,
    opciones: [
      OpcionSelect(valor: 'aprobado', label: 'Aprobado'),
      OpcionSelect(valor: 'pendiente', label: 'Pendiente'),
      OpcionSelect(valor: 'rechazado', label: 'Rechazado'),
    ],
  ),
];

// En el build:
FormDinamico(
  campos: campos,
  valoresIniciales: datosSeccion ?? {},
  textoBoton: 'Guardar sección',
  onEnviar: (valores) {
    tramitesService.guardarSeccion(seccionId, valores);
  },
);
```

---

## 6. Commit

```bash
git add .
git commit -m "feat(mobile/widgets): FormDinamico para formularios definidos por datos"
```

---

## Próximo paso

Continuar con **`07_widget_timeline_custom.md`**.
