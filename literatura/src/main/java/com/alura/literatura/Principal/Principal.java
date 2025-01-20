package com.alura.literatura.Principal;
import com.alura.literatura.Model.Autor;
import com.alura.literatura.Model.Datos;
import com.alura.literatura.Model.DatosAutor;
import com.alura.literatura.Model.DatosLibros;
import com.alura.literatura.Model.Libro;
import com.alura.literatura.Repository.AutorRepository;
import com.alura.literatura.Repository.LibroRepository;
import com.alura.literatura.Service.ConsumoAPI;
import com.alura.literatura.Service.ConvierteDatos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

@Component
public class Principal {
    private static final String URL_BASE = "https://gutendex.com/books/";

    @Autowired
    private ConsumoAPI consumoAPI;

    @Autowired
    private ConvierteDatos conversor;

    @Autowired
    private AutorRepository autorRepository;

    @Autowired
    private LibroRepository libroRepository;

    private final Scanner teclado;
    private boolean ejecutando;

    public Principal() {
        this.teclado = new Scanner(System.in);
        this.ejecutando = true;
    }

    public void muestraElMenu() {
        while (ejecutando) {
            try {
                mostrarOpciones();
                int opcion = leerOpcion();
                procesarOpcion(opcion);
            } catch (Exception e) {
                System.out.println("Error al procesar la opción: " + e.getMessage());
                teclado.nextLine(); // Limpiar el buffer
            }
        }
    }

    private void mostrarOpciones() {
        System.out.println("********* Libreria Alura ***********");
        System.out.println("1- Buscar libro por título");
        System.out.println("2- Listar libros registrados");
        System.out.println("3- Listar autores registrados");
        System.out.println("4- Listar autores vivos en un determinado año");
        System.out.println("5- Listar libros por idioma");
        System.out.println("0- Salir");
        System.out.println("*****************************************");
        System.out.print("Selecciona la opción deseada: ");
    }

    private int leerOpcion() {
        int opcion = teclado.nextInt();
        teclado.nextLine();
        return opcion;
    }

    private void procesarOpcion(int opcion) {
        switch (opcion) {
            case 1 -> buscarLibroPorTitulo();
            case 2 -> listarLibrosRegistrados();
            case 3 -> listarAutoresRegistrados();
            case 4 -> listarAutoresVivosPorAnio();
            case 5 -> listarLibrosPorIdioma();
            case 0 -> salir();
            default -> System.out.println("La opción no válida.");
        }
    }

    private void listarAutoresRegistrados() {
        System.out.println("**********\nAutores Registrados*******");

        List<Autor> autores = autorRepository.findAutoresConLibros();
        if (autores.isEmpty()) {
            System.out.println("No hay autores registrados en la base de datos.");
            return;
        }

        for (Autor autor : autores) {
            String fechaNacimiento = autor.getFechaDeNacimiento() != null ? autor.getFechaDeNacimiento() : "No disponible";
            String fechaFallecimiento = autor.getFechaFallecimiento() != null ? autor.getFechaFallecimiento() : "No disponible";

            System.out.printf("Autor: %s | Fecha de nacimiento: %s | Fecha de fallecimiento: %s%n",
                    autor.getNombre(), fechaNacimiento, fechaFallecimiento);

            System.out.println("Libros:");
            autor.getLibros().forEach(libro -> System.out.printf("- %s (Idioma: %s, Descargas: %s)%n",
                    libro.getTitulo(), libro.getIdioma(), libro.getNumeroDeDescargas()));
            System.out.println("--------------------------------------------------");
        }
    }

    private void buscarLibroPorTitulo() {
        try {
            System.out.println("\n***** Buscar Libro ******");
            System.out.print("Ingrese el título del libro: ");
            String tituloLibro = teclado.nextLine().trim();

            if (tituloLibro.isEmpty()) {
                System.out.println("El título del libro no puede estar vacío.");
                return;
            }


            Optional<Libro> libroEnBD = libroRepository.findByTituloContainingIgnoreCase(tituloLibro);
            if (libroEnBD.isPresent()) {
                System.out.println("\nLibro encontrado:");
                System.out.println(libroEnBD.get());
                return;
            }

            System.out.println("Buscando en la API externa...");
            String urlBusqueda = URL_BASE + "?search=" + URLEncoder.encode(tituloLibro, StandardCharsets.UTF_8);
            String json = consumoAPI.obtenerDatos(urlBusqueda);

            if (json == null || json.isEmpty()) {
                System.out.println("No se recibió respuesta de la API");
                return;
            }

            Datos datosBusqueda = conversor.obtenerDatos(json, Datos.class);

            if (datosBusqueda.resultados() == null || datosBusqueda.resultados().isEmpty()) {
                System.out.println("No se encontraron resultados para: " + tituloLibro);
                return;
            }

            procesarResultadosBusqueda(datosBusqueda.resultados(), tituloLibro);

        } catch (Exception e) {
            System.out.println("Error durante la búsqueda: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void procesarResultadosBusqueda(List<DatosLibros> resultados, String tituloLibro) {
        boolean encontrado = false;
        for (DatosLibros datosLibro : resultados) {
            if (datosLibro.titulo().toUpperCase().contains(tituloLibro.toUpperCase())) {
                guardarLibroYAutor(datosLibro);
                encontrado = true;
                break;
            }
        }

        if (!encontrado) {
            System.out.println("No se encontró ningún libro que coincida con: " + tituloLibro);
        }
    }

    private void guardarLibroYAutor(DatosLibros datosLibro) {
        try {
            if (datosLibro.autor() == null || datosLibro.autor().isEmpty()) {
                System.out.println("El libro no tiene autor registrado.");
                return;
            }


            DatosAutor datosAutor = datosLibro.autor().get(0);
            Autor autor = autorRepository.findByNombre(datosAutor.nombre())
                    .orElseGet(() -> {
                        Autor nuevoAutor = new Autor(datosAutor);
                        System.out.println("Guardando nuevo autor: " + datosAutor.nombre());
                        return autorRepository.save(nuevoAutor);
                    });

            // Verificar si el libro ya existe
            if (libroRepository.findByTituloContainingIgnoreCase(datosLibro.titulo()).isPresent()) {
                System.out.println("El libro ya existe en la base de datos.");
                return;
            }

            Libro libro = new Libro(datosLibro, autor);
            Libro libroGuardado = libroRepository.save(libro);
            System.out.println("\nLibro guardado exitosamente:");
            System.out.println(libroGuardado);

        } catch (Exception e) {
            System.out.println("Error al guardar el libro y autor: " + e.getMessage());
        }
    }

    private void listarLibrosRegistrados() {
        System.out.println("\n*********** Libros Registrados ************");
        List<Libro> libros = libroRepository.findAll();
        if (libros.isEmpty()) {
            System.out.println("No hay libros registrados en la base de datos.");
            return;
        }
        libros.forEach(System.out::println);
    }

    private void listarAutoresVivosPorAnio() {
        try {
            System.out.println("\n******* Autores vivos por año *******");
            System.out.print("Ingrese el año para consultar: ");
            int ano = Integer.parseInt(teclado.nextLine().trim());

            if (ano < 0 || ano > 2024) {
                System.out.println("Por favor, ingrese un año válido.");
                return;
            }

            System.out.println("Autores vivos en " + ano + ":");
            List<Autor> autores = autorRepository.findAll();
            boolean encontrados = false;

            for (Autor autor : autores) {
                if (estaVivoEnAnio(autor, ano)) {
                    System.out.println("- " + autor.getNombre() +
                            " (Nacimiento: " + autor.getFechaDeNacimiento() + ")");
                    encontrados = true;
                }
            }

            if (!encontrados) {
                System.out.println("No se encontraron autores vivos para el año especificado.");
            }

        } catch (NumberFormatException e) {
            System.out.println("Ingresa un año válido en formato numérico.");
        } catch (Exception e) {
            System.out.println("Error al procesar la consulta: " + e.getMessage());
        }
    }

    private boolean estaVivoEnAnio(Autor autor, int anio) {
        String fechaNacimiento = autor.getFechaDeNacimiento();
        if (fechaNacimiento == null || fechaNacimiento.isEmpty()) {
            return false;
        }
        try {
            int anoNacimiento = Integer.parseInt(fechaNacimiento);
            return anoNacimiento <= anio;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void listarLibrosPorIdioma() {
        System.out.println("\n******* Libros por idioma *****");
        System.out.println("Idiomas disponibles: ES (Español), EN (Inglés), FR (Francés), PT (Portugués)");
        System.out.print("Ingrese el código del idioma: ");

        String idioma = teclado.nextLine().trim().toUpperCase();
        if (!esIdiomaValido(idioma)) {
            System.out.println("El idioma no válido. Use: ES, EN, FR o PT");
            return;
        }

        List<Libro> libros = libroRepository.findAll();
        List<Libro> librosFiltrados = libros.stream()
                .filter(libro -> libro.getIdioma().equalsIgnoreCase(idioma))
                .toList();

        if (librosFiltrados.isEmpty()) {
            System.out.println("No se encontraron libros en " + obtenerNombreIdioma(idioma));
            return;
        }

        System.out.println("\nLibros en " + obtenerNombreIdioma(idioma) + ":");
        librosFiltrados.forEach(libro ->
                System.out.printf("- %s (Autor: %s)%n",
                        libro.getTitulo(),
                        libro.getAutor().getNombre())
        );
    }

    private boolean esIdiomaValido(String idioma) {
        return idioma.matches("^(ES|EN|FR|PT)$");
    }

    private String obtenerNombreIdioma(String codigo) {
        return switch (codigo) {
            case "ES" -> "Español";
            case "EN" -> "Inglés";
            case "FR" -> "Francés";
            case "PT" -> "Portugués";
            default -> codigo;
        };
    }

    private void salir() {
        System.out.println("\n¡Gracias por usar la libreria Alura! Hasta pronto.");
        ejecutando = false;
    }
}