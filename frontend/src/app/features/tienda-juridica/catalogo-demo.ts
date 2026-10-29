export const CATALOGO_DEMO = [
{id:-1,nombre:'Código Civil Peruano',categoria:'Códigos y leyes',descripcion:'Ejemplo de catálogo para la presentación. No disponible para compra.',precio:90,icono:'§'},
{id:-2,nombre:'Código Penal Peruano',categoria:'Códigos y leyes',descripcion:'Producto de demostración, sin entrega ni cobro.',precio:85,icono:'§'},
{id:-3,nombre:'Constitución Política del Perú',categoria:'Códigos y leyes',descripcion:'Producto de demostración para explorar la tienda.',precio:45,icono:'§'},
{id:-4,nombre:'Código Procesal Civil',categoria:'Códigos y leyes',descripcion:'Producto de demostración, sin entrega ni cobro.',precio:80,icono:'§'},
{id:-5,nombre:'Escritos jurídicos',categoria:'Modelos de escritos',descripcion:'Ejemplo de recurso jurídico. Datos de demostración.',precio:60,icono:'▧'},
{id:-6,nombre:'Manual de Derecho Laboral',categoria:'Libros jurídicos',descripcion:'Ejemplo de libro especializado. Datos de demostración.',precio:75,icono:'▤'},
...['Civil','Penal','Laboral','Empresarial'].map((area,i)=>({id:-7-i,nombre:'Asesoría en Derecho '+area,categoria:'Asesorías',descripcion:'Servicio de demostración. No reserva una cita real.',precio:150,icono:'⚖'}))
];
