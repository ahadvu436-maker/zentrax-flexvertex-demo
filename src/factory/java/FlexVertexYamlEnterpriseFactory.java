/******************************************************************************
 *
 * FlexVertex Example Factory
 *
 * Workflow: YAML-Driven Enterprise Factory
 *
 * ----------------------------------------------------------------------------
 * Overview
 * ----------------------------------------------------------------------------
 *
 * This example proves a narrow but complete pipeline:
 *
 *   YAML enterprise description
 *       -> Java configuration objects
 *       -> canonical FlexVertex Java DDL
 *       -> canonical FlexVertex Java DML
 *       -> persisted FlexVertex relationships
 *       -> generated PDF assets ingested into FlexVertex
 *       -> generated Voyager scripts written to disk and ingested as assets
 *       -> document evidence connected to enterprise objects
 *
 * Vertical applications are split into focused YAML modules loaded by a
 * root manifest. The Java interpreter remains generic: new classes, properties,
 * objects, relationships, documents, and traversals can be introduced by editing
 * YAML rather than rewriting the FlexVertex persistence engine. Monolithic YAML
 * remains supported for backward compatibility.
 *
 * The YAML also declares a small document set. Java generates those PDFs at
 * runtime, ingests them as first-class FlexVertex assets, extracts their text,
 * generates vector embeddings, and persists indexed EmbeddingChunk objects.
 * Document querying and hybrid search remain deliberately out of scope.
 *
 ******************************************************************************/

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import com.flexvertex.messaging.client.drivers.FlexDirectClientDriver;
import com.flexvertex.messaging.client.interfaces.IFlexClientDriver;

import com.flexvertex.multiverse.client.FlexMultiverse;
import com.flexvertex.universe.components.constants.FlexConstants.FlexType;
import com.flexvertex.multiverse.shared.assets.FlexAsset;
import com.flexvertex.multiverse.shared.assets.FlexImage;
import com.flexvertex.multiverse.shared.connections.FlexConnection;
import com.flexvertex.multiverse.shared.config.sp.embedding.FlexEmbeddingProviderConfig;
import com.flexvertex.multiverse.shared.interfaces.sp.embedding.IFlexEmbeddingProvider;
import com.flexvertex.multiverse.shared.interfaces.sp.embedding.config.IFlexEmbeddingProviderConfig;
import com.flexvertex.multiverse.shared.interfaces.sp.extraction.IFlexExtractionMetadata;
import com.flexvertex.multiverse.shared.interfaces.sp.extraction.IFlexExtractionProvider;
import com.flexvertex.multiverse.shared.objects.FlexObject;
import com.flexvertex.multiverse.shared.sp.embedding.result.FlexEmbedding;
import com.flexvertex.multiverse.shared.sp.embedding.result.FlexEmbeddingResult;
import com.flexvertex.multiverse.shared.sp.extraction.text.FlexTextExtractionMetadata;
import com.flexvertex.multiverse.shared.sp.index.lucenehybrid.config.FlexLuceneQuantizedVectorField;
import com.flexvertex.multiverse.shared.sp.index.lucenehybrid.config.FlexLuceneQuantizedVectorField.QuantizedVectorFormat;
import com.flexvertex.multiverse.shared.sp.index.lucenehybrid.config.FlexLuceneVectorField.VectorSimilarity;
import com.flexvertex.multiverse.shared.interfaces.IFlexClass;
import com.flexvertex.multiverse.shared.interfaces.IFlexConnection;
import com.flexvertex.multiverse.shared.interfaces.IFlexDomain;
import com.flexvertex.multiverse.shared.interfaces.IFlexFolder;
import com.flexvertex.multiverse.shared.interfaces.IFlexNexus;
import com.flexvertex.multiverse.shared.interfaces.IFlexObject;
import com.flexvertex.multiverse.shared.interfaces.IFlexSchema;
import com.flexvertex.multiverse.shared.interfaces.IFlexSession;
import com.flexvertex.multiverse.shared.path.FlexPath;
import com.flexvertex.multiverse.shared.security.FlexUserAccount;
import com.flexvertex.multiverse.shared.transaction.FlexAutoSessionTx;

import com.flexvertex.universe.components.time.FlexDateTime;
import com.flexvertex.security.shared.interfaces.IFlexAuthenticationToken;
import com.flexvertex.security.shared.secrets.FlexPasswordSecret;
import com.flexvertex.security.shared.tokens.FlexUsernamePasswordToken;

/**
 * Generic YAML-to-FlexVertex materializer.
 *
 * The interpreter knows nothing about retail. It knows how to:
 *
 *   1. validate the declarative configuration;
 *   2. map YAML type names to FlexType values;
 *   3. create data classes plus named and class-based connection definitions;
 *   4. convert YAML scalar values to their declared FlexVertex types;
 *   5. create objects and retain their YAML IDs in a Java registry;
 *   6. resolve relationship endpoints and create saved IFlexConnection objects;
 *   7. generate YAML-declared PDFs and ingest them with createAssetFromPath();
 *   8. extract PDF text, generate embeddings, and persist indexed chunks;
 *   9. create a graph object for each PDF and connect it to YAML-declared entities;
 *  10. connect every embedding chunk to its source document;
 *  11. seed passage-level chunk-to-entity evidence links from YAML phrase rules;
 *  12. generate scenario documentation and startup notes from YAML metadata;
 *  13. generate vertical-specific Voyager traversals from YAML;
 *  14. declare business-key unique IDs on FlexVertex classes from YAML;
 *  15. select named or class-based connection materialization from YAML.
 */
public class FlexVertexYamlEnterpriseFactory {

    private static final String DEFAULT_CONFIG =
        "examples/hello-graph/hello-graph.yaml";

    private static final List<String> loadedConfigurationModules =
        new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : DEFAULT_CONFIG;

        try {
            FlexMultiverse.initialize();
            EnterpriseDefinition definition = loadDefinition(configPath);
            new FlexVertexYamlEnterpriseFactory().execute(definition, configPath);
        } catch (Exception ex) {
            System.out.println("YAML enterprise factory failed: " + ex);
            ex.printStackTrace();
        }

        System.exit(0);
    }

    private static EnterpriseDefinition loadDefinition(String configPath)
        throws Exception {
        Path rootPath = Path.of(configPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(rootPath)) {
            throw new IllegalArgumentException(
                "YAML configuration file does not exist: " + rootPath
            );
        }

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        loadedConfigurationModules.clear();
        Set<Path> activeIncludes = new LinkedHashSet<>();
        ObjectNode merged = loadAndMergeYaml(rootPath, mapper, activeIncludes);

        EnterpriseDefinition definition = mapper.treeToValue(
            merged,
            EnterpriseDefinition.class
        );
        definition.normalize();
        definition.validate();
        return definition;
    }

    /**
     * Loads either a monolithic enterprise YAML file or a modular manifest.
     *
     * A file may declare an ordered `includes:` list. Included paths are resolved
     * relative to the file that declares them. Objects are merged recursively,
     * arrays are appended in include order, and scalar conflicts are rejected.
     * Circular includes are rejected before materialization begins.
     */
    private static ObjectNode loadAndMergeYaml(
        Path yamlPath,
        ObjectMapper mapper,
        Set<Path> activeIncludes
    ) throws Exception {
        Path normalizedPath = yamlPath.toAbsolutePath().normalize();

        if (!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException(
                "Included YAML module does not exist: " + normalizedPath
            );
        }
        if (!activeIncludes.add(normalizedPath)) {
            throw new IllegalArgumentException(
                "Circular YAML include detected at: " + normalizedPath
            );
        }

        JsonNode parsed = mapper.readTree(normalizedPath.toFile());
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalArgumentException(
                "YAML module must contain a mapping at its root: " + normalizedPath
            );
        }

        loadedConfigurationModules.add(normalizedPath.toString());

        ObjectNode local = ((ObjectNode) parsed).deepCopy();
        JsonNode includesNode = local.remove("includes");

        ObjectNode merged = mapper.createObjectNode();

        if (includesNode != null) {
            if (!includesNode.isArray()) {
                throw new IllegalArgumentException(
                    "`includes` must be a YAML list in " + normalizedPath
                );
            }

            for (JsonNode includeNode : includesNode) {
                if (!includeNode.isTextual() || includeNode.asText().isBlank()) {
                    throw new IllegalArgumentException(
                        "Every include must be a nonblank relative path in "
                            + normalizedPath
                    );
                }

                Path includedPath = normalizedPath.getParent()
                    .resolve(includeNode.asText())
                    .normalize();

                ObjectNode included = loadAndMergeYaml(
                    includedPath,
                    mapper,
                    activeIncludes
                );
                mergeYamlNodes(merged, included, includedPath.toString());
            }
        }

        mergeYamlNodes(merged, local, normalizedPath.toString());
        activeIncludes.remove(normalizedPath);
        return merged;
    }

    /**
     * Merges one YAML object into another without silently overwriting values.
     */
    private static void mergeYamlNodes(
        ObjectNode target,
        ObjectNode source,
        String sourceDescription
    ) {
        source.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode incoming = entry.getValue();
            JsonNode existing = target.get(fieldName);

            if (existing == null || existing.isNull()) {
                target.set(fieldName, incoming.deepCopy());
                return;
            }

            if (existing.isObject() && incoming.isObject()) {
                mergeYamlNodes(
                    (ObjectNode) existing,
                    (ObjectNode) incoming,
                    sourceDescription + "#" + fieldName
                );
                return;
            }

            if (existing.isArray() && incoming.isArray()) {
                ArrayNode targetArray = (ArrayNode) existing;
                for (JsonNode item : incoming) {
                    targetArray.add(item.deepCopy());
                }
                return;
            }

            if (existing.equals(incoming)) {
                return;
            }

            throw new IllegalArgumentException(
                "Conflicting YAML value for '" + fieldName
                    + "' while merging " + sourceDescription
            );
        });
    }

    public void execute(EnterpriseDefinition definition, String configPath) throws Exception {
        FlexVertexSettings fv = definition.flexvertex;
        IFlexClientDriver driver = new FlexDirectClientDriver(fv.host, fv.port);

        writeGeneratedScenarioNotes(definition);
        writeVoyagerScripts(definition);
        printPlan(definition, configPath);

        IFlexAuthenticationToken adminToken = new FlexUsernamePasswordToken(
            fv.adminPath,
            fv.adminPassword
        );

        try (IFlexSession adminSession = FlexMultiverse.openSession(driver, adminToken)) {
            setupDomain(adminSession, definition);
        }

        IFlexAuthenticationToken demoToken = new FlexUsernamePasswordToken(
            fv.userPath().getPath(),
            fv.userPassword
        );

        try (IFlexSession demoSession = FlexMultiverse.openSession(driver, demoToken)) {
            // Create image assets and classes as the same schema user whose Home
            // folder Commander exposes. This mirrors the proven PDF asset lifecycle
            // and ensures class default images reference user-owned persisted assets.
            setupSchema(demoSession, definition);
            materializeData(demoSession, definition);
        }

        printCompletion(definition);
    }

    private void printPlan(EnterpriseDefinition definition, String configPath) {
        System.out.println();
        System.out.println("============================================================");
        System.out.println(" FLEXVERTEX YAML ENTERPRISE FACTORY");
        System.out.println("============================================================");
        System.out.println("Configuration: " + configPath);
        System.out.println("Loaded YAML modules:");
        for (String module : loadedConfigurationModules) {
            System.out.println("  - " + module);
        }
        System.out.println("Factory:       " + definition.factory.name);
        System.out.println("Target schema: " + definition.flexvertex.schemaPath());
        System.out.println();
        System.out.println("The YAML requests:");
        System.out.println("  Data classes:       " + definition.classes.size());
        long namedConnectionDefinitions = definition.connectionClasses.stream()
            .filter(ClassDefinition::isNamedConnection)
            .count();
        long classBasedConnectionDefinitions =
            definition.connectionClasses.size() - namedConnectionDefinitions;

        System.out.println("  Connection classes: " + definition.connectionClasses.size());
        System.out.println("    Named:            " + namedConnectionDefinitions);
        System.out.println("    Class-based:      " + classBasedConnectionDefinitions);
        System.out.println("  Objects:            " + definition.objects.size());
        System.out.println("  Relationships:      " + definition.relationships.size());
        System.out.println("  PDF documents:      " + definition.documents.size());
        System.out.println("  Class icons:        " + definition.icons.assignments.size());
        System.out.println("  Voyager scripts:    " + definition.voyagerScripts.size());
        System.out.println("  Voyager assets:     " + definition.voyagerScripts.size());
        System.out.println("  Embedding enabled:  " + definition.embedding.enabled);

        if (definition.factory.graphConstruction != null) {
            System.out.println();
            System.out.println("Graph-construction note:");
            System.out.println("  Demo: " + definition.factory.graphConstruction.demoMode);
            for (String note : definition.factory.graphConstruction.stdoutNotes) {
                System.out.println("  - " + note);
            }
            System.out.println("  Generated notes: "
                + definition.factory.graphConstruction.generatedNotesPath);
        }
    }

    private void writeGeneratedScenarioNotes(EnterpriseDefinition definition)
        throws IOException {
        GraphConstructionNotes notes = definition.factory.graphConstruction;
        if (notes == null || !notBlank(notes.generatedNotesPath)) {
            return;
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Generated Scenario Notes\n\n");
        markdown.append("**Factory:** ").append(definition.factory.name).append("\n\n");
        if (notBlank(definition.factory.description)) {
            markdown.append(definition.factory.description).append("\n\n");
        }
        markdown.append("## How this demonstration builds the graph\n\n");
        markdown.append(notes.demoMode).append("\n\n");
        markdown.append("## How a production graph would likely be built\n\n");
        for (String item : notes.realWorldMode) {
            markdown.append("- ").append(item).append("\n");
        }
        markdown.append("\n## Runtime notes\n\n");
        for (String item : notes.stdoutNotes) {
            markdown.append("- ").append(item).append("\n");
        }
        markdown.append("\n## Passage-level evidence model\n\n");
        markdown.append("Every extracted `EmbeddingChunk` is connected to its parent ")
            .append("`DocumentEvidence` object. YAML phrase rules then seed selected ")
            .append("`chunkSupportsEntity` relationships to suppliers, products, ")
            .append("certifications, reviews, changes, and policies. The relationship ")
            .append("stores provenance, confidence, match method, and authority status.\n");

        Path path = Path.of(notes.generatedNotesPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, markdown.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Writes vertical-specific Voyager scripts declared in YAML.
     *
     * Keeping these scripts in the vertical specification means the same YAML
     * owns the graph model, seeded evidence relationships, and the traversals
     * that demonstrate those relationships.
     */
    private void writeVoyagerScripts(EnterpriseDefinition definition)
        throws IOException {
        for (VoyagerScriptDefinition script : definition.voyagerScripts) {
            Path path = Path.of(script.path);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String content = script.content;
            if (!content.endsWith("\n")) {
                content += "\n";
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }
    }

    /**
     * Recreates only the YAML-owned demo domain. The domain name is intentionally
     * separate from the canonical FlexVertexDemos domain used by other examples.
     */
    private void setupDomain(IFlexSession session, EnterpriseDefinition definition) {
        FlexVertexSettings fv = definition.flexvertex;
        System.out.println("\n=== Creating YAML-owned domain, nexus, schema, and user ===");

        try (FlexAutoSessionTx autoTx = new FlexAutoSessionTx(session)) {
            if (fv.recreateDomain && session.domainExists(fv.domain)) {
                session.deleteDomain(fv.domain);
            }

            IFlexDomain domain = session.createDomain(fv.domain);
            IFlexNexus nexus = domain.createNexus(fv.nexus);
            IFlexSchema schema = nexus.createSchema(fv.schema);

            System.out.println("Schema ID = " + schema.getSchemaID());
            schema.close();

            try (IFlexSchema innerSchema = nexus.openSchema(fv.schema)) {
                innerSchema.createUserAccount(
                    fv.user,
                    new FlexPasswordSecret(fv.userPassword)
                );
            }

            nexus.close();
            domain.close();
        }

        System.out.println("Environment creation complete; demo user is ready.");
    }

    /**
     * Converts YAML class declarations into the same explicit FlexVertex DDL
     * calls used by the canonical project.
     */
    private void setupSchema(IFlexSession session, EnterpriseDefinition definition) {
        System.out.println("\n=== Interpreting YAML as FlexVertex DDL ===");

        try (FlexAutoSessionTx autoTx = new FlexAutoSessionTx(session)) {
            try (IFlexSchema schema = session.openSchema(definition.flexvertex.schemaPath())) {
                Map<String, FlexImage> classIcons = loadClassIcons(session, schema, definition.icons);
                for (ClassDefinition classDefinition : definition.classes) {
                    IFlexClass flexClass = schema.createClass(classDefinition.name);
                    createProperties(flexClass, classDefinition.properties);

                    if (notBlank(classDefinition.labelProperty)) {
                        flexClass.setLabelFormat("%s", classDefinition.labelProperty);
                    }

                    if (!classDefinition.uniqueIDProperties.isEmpty()) {
                        flexClass.setUniqueIDProperties(
                            classDefinition.uniqueIDProperties.toArray(new String[0])
                        );
                    }

                    FlexImage classIcon = classIcons.get(classDefinition.name);
                    if (classIcon != null) {
                        flexClass.setDefaultImage(classIcon);
                    }

                    flexClass.save();
                    System.out.println(
                        "  Created data class: " + classDefinition.name
                            + (classDefinition.uniqueIDProperties.isEmpty()
                                ? ""
                                : " [unique ID: "
                                    + String.join(", ", classDefinition.uniqueIDProperties)
                                    + "]")
                    );
                }

                for (ClassDefinition classDefinition : definition.connectionClasses) {
                    IFlexClass flexClass;

                    if (classDefinition.isClassBasedConnection()) {
                        // Canonical modeled connection type. This is intended for
                        // durable domain semantics that deserve an explicit class.
                        flexClass = schema.createConnectionClass(classDefinition.name);
                    } else {
                        // Canonical named connection definition. connectTo(..., name)
                        // remains the quick, informal way to join two objects.
                        flexClass = schema.createClass(
                            classDefinition.name,
                            "sysclass:FlexConnection"
                        );
                    }

                    createProperties(flexClass, classDefinition.properties);
                    flexClass.save();
                    System.out.println(
                        "  Created " + classDefinition.connectionModeLabel()
                            + " connection: " + classDefinition.name
                    );
                }

                if (definition.embedding.enabled) {
                    createEmbeddingInfrastructure(schema, definition.embedding, classIcons);
                }

                System.out.println("  Persisting class definitions to schema...");
                schema.save();
                System.out.println("  Schema DDL persisted successfully.");
            } catch (Throwable ex) {
                System.out.println("setupSchema() Exception: " + ex);
                ex.printStackTrace();
                throw new RuntimeException("Unable to create schema from YAML.", ex);
            }
        }
    }

    private void createEmbeddingInfrastructure(
        IFlexSchema schema,
        EmbeddingSettings embedding,
        Map<String, FlexImage> classIcons
    ) throws Exception {
        IFlexClass embeddingChunk = schema.createClass(embedding.chunkClass);
        embeddingChunk.createProperty("Text", FlexType.String);
        embeddingChunk.createProperty("Vector", FlexType.Vector);
        embeddingChunk.createProperty("AssetKey", com.flexvertex.multiverse.shared.constants.FlexConstants.FlexType.Key);
        embeddingChunk.createProperty("DocumentID", FlexType.String);
        embeddingChunk.createProperty("DocumentType", FlexType.String);
        embeddingChunk.createProperty("Title", FlexType.String);
        embeddingChunk.createProperty("PageNumber", FlexType.Integer);
        embeddingChunk.createProperty("Section", FlexType.String);

        FlexImage embeddingChunkIcon = classIcons.get(embedding.chunkClass);
        if (embeddingChunkIcon != null) {
            embeddingChunk.setDefaultImage(embeddingChunkIcon);
        }
        embeddingChunk.save();

        IFlexEmbeddingProviderConfig embConfig = createEmbeddingConfig(embedding);
        embeddingChunk
            .createHybridIndexBuilder(embedding.indexName)
            .addTextField("Text")
            .addField(new FlexLuceneQuantizedVectorField(
                "Vector",
                QuantizedVectorFormat.FourBits,
                embedding.vectorDimensions,
                VectorSimilarity.Cosine,
                embConfig
            ))
            .addStringField("DocumentID")
            .addStringField("DocumentType")
            .create();
        embeddingChunk.save();

        System.out.println("  Created embedding class: " + embedding.chunkClass);
        System.out.println("  Created hybrid index:   " + embedding.indexName);
    }

    private IFlexEmbeddingProviderConfig createEmbeddingConfig(
        EmbeddingSettings embedding
    ) {
        IFlexEmbeddingProviderConfig config = new FlexEmbeddingProviderConfig();
        config.setProviderName(embedding.providerName);
        config.setProperty("Model", embedding.modelPath);
        config.setProperty("Tokenizer", embedding.tokenizerPath);
        config.setProperty("MaxTokens", embedding.maxTokens);
        config.setProperty("Config", embedding.configPath);
        config.setProperty("Detector", embedding.detectorPath);
        return config;
    }

    private Map<String, FlexImage> loadClassIcons(
        IFlexSession session,
        IFlexSchema schema,
        IconSettings icons
    ) throws Exception {
        Map<String, FlexImage> result = new LinkedHashMap<>();
        if (icons.assignments.isEmpty()) {
            return result;
        }

        FlexUserAccount currentUser = (FlexUserAccount) session.getUserAccount();
        IFlexFolder folder = currentUser.getHomeFolder();
        for (String segment : icons.assetFolder.split("/")) {
            if (!notBlank(segment)) {
                continue;
            }
            if (folder.folderExists(segment)) {
                folder.deleteFolder(segment);
            }
            folder = folder.createFolder(segment);
        }

        // Follow the same proven lifecycle used for PDF assets: create the asset,
        // attach it to the current schema user's persistent Home-folder tree, and
        // only then save the schema. All icons are persisted before any class is
        // created or assigned a default image.
        for (IconAssignment assignment : icons.assignments) {
            File source = new File(assignment.path);
            if (!source.isFile() || source.length() == 0L) {
                throw new IllegalStateException("Class icon is missing or empty: " + source.getAbsolutePath());
            }
            FlexImage image = (FlexImage) schema.createAssetFromPath(assignment.path);
            folder.add(image);
            result.put(assignment.className, image);
            System.out.println("  Created class icon asset: " + assignment.className + " <- " + assignment.assetName);
        }

        // Critical ordering contract: persist every image and folder membership
        // before createClass() is called and before setDefaultImage() references it.
        schema.save();
        System.out.println("  Persisted " + result.size() + " class icon assets before class creation.");
        return result;
    }

    private void createProperties(
        IFlexClass flexClass,
        List<PropertyDefinition> properties
    ) throws Exception {
        for (PropertyDefinition property : properties) {
            flexClass.createProperty(property.name, toFlexType(property.type));
        }
    }

    /**
     * Converts YAML object and relationship declarations into canonical
     * FlexVertex DML calls.
     */
    private void materializeData(
        IFlexSession session,
        EnterpriseDefinition definition
    ) throws Exception {
        System.out.println("\n=== Interpreting YAML as FlexVertex DML ===");

        FlexUserAccount userAccount = (FlexUserAccount) session.getUserAccount();
        IFlexSchema schema = userAccount.getSchema();

        Map<String, ClassDefinition> classDefinitions =
            indexClassDefinitions(definition.classes);
        Map<String, ClassDefinition> connectionDefinitions =
            indexClassDefinitions(definition.connectionClasses);
        Map<String, IFlexObject> objectRegistry = new LinkedHashMap<>();

        for (ObjectDefinition objectDefinition : definition.objects) {
            ClassDefinition classDefinition = classDefinitions.get(
                objectDefinition.className
            );
            if (classDefinition == null) {
                throw new IllegalArgumentException(
                    "Object '" + objectDefinition.id + "' refers to undefined class '"
                        + objectDefinition.className + "'."
                );
            }

            IFlexObject flexObject = schema.createObject(objectDefinition.className);
            applyProperties(
                flexObject,
                objectDefinition.properties,
                classDefinition.propertyIndex()
            );
            flexObject.save();
            // The YAML id is only a factory-local alias used to resolve later
            // relationships. FlexVertex persistence uses the class's declared
            // unique-ID properties when present.
            objectRegistry.put(objectDefinition.id, flexObject);

            System.out.println(
                "  Created object " + objectDefinition.id
                    + " as " + objectDefinition.className
                    + " with key " + flexObject.getObjectKey()
            );
        }

        schema.save();

        System.out.println("\n=== Creating YAML-declared relationships ===");

        for (RelationshipDefinition relationship : definition.relationships) {
            IFlexObject source = objectRegistry.get(relationship.from);
            IFlexObject target = objectRegistry.get(relationship.to);

            if (source == null) {
                throw new IllegalArgumentException(
                    "Relationship source does not exist: " + relationship.from
                );
            }
            if (target == null) {
                throw new IllegalArgumentException(
                    "Relationship target does not exist: " + relationship.to
                );
            }

            ClassDefinition connectionDefinition = connectionDefinitions.get(
                relationship.connectionClass
            );
            if (connectionDefinition == null) {
                throw new IllegalArgumentException(
                    "Relationship refers to undefined connection class: "
                        + relationship.connectionClass
                );
            }

            IFlexConnection connection;
            if (connectionDefinition.isClassBasedConnection()) {
                connection = new FlexConnection(
                    schema,
                    relationship.connectionClass,
                    source,
                    target
                );
            } else {
                connection = source.connectTo(
                    target,
                    relationship.connectionClass
                );
            }

            applyProperties(
                connection,
                relationship.properties,
                connectionDefinition.propertyIndex()
            );
            connection.save();

            // Named connections are attached through the source object's quick
            // connect API. Saving the source preserves the canonical lifecycle.
            if (connectionDefinition.isNamedConnection()) {
                source.save();
            }

            System.out.println(
                "  Connected " + relationship.from + " -["
                    + relationship.connectionClass + "; "
                    + connectionDefinition.connectionModeLabel()
                    + "]-> " + relationship.to
            );
        }

        schema.save();

        // Match the canonical asset lifecycle: assets must be attached to a
        // persistent folder owned by the schema user before an extraction
        // provider can reload them by object key.
        final String embeddedDocsFolderName = "EmbeddedDocuments";
        if (userAccount.getHomeFolder().folderExists(embeddedDocsFolderName)) {
            userAccount.getHomeFolder().deleteFolder(embeddedDocsFolderName);
        }
        IFlexFolder embeddedDocs =
            userAccount.getHomeFolder().createFolder(embeddedDocsFolderName);

        generateIngestAndEmbedDocuments(
            schema,
            embeddedDocs,
            definition.documents,
            definition.embedding,
            classDefinitions,
            connectionDefinitions,
            objectRegistry
        );
        schema.save();

        ingestVoyagerScriptsAsAssets(userAccount, schema, definition.voyagerScripts);
        schema.save();
    }

    /**
     * Ingests each generated Voyager script into the current user's Home/Voyager
     * folder. The filesystem copy remains the canonical editable source; the
     * FlexVertex asset is a convenience copy that can be opened and run directly
     * from Commander without an external editor or copy/paste step.
     */
    private void ingestVoyagerScriptsAsAssets(
        FlexUserAccount userAccount,
        IFlexSchema schema,
        List<VoyagerScriptDefinition> voyagerScripts
    ) throws Exception {
        if (voyagerScripts.isEmpty()) {
            return;
        }

        final String voyagerFolderName = "Voyager";
        IFlexFolder homeFolder = userAccount.getHomeFolder();
        if (homeFolder.folderExists(voyagerFolderName)) {
            homeFolder.deleteFolder(voyagerFolderName);
        }
        IFlexFolder voyagerFolder = homeFolder.createFolder(voyagerFolderName);

        System.out.println("\n=== Ingesting Voyager scripts as Commander assets ===");
        for (VoyagerScriptDefinition script : voyagerScripts) {
            File source = new File(script.path);
            if (!source.isFile() || source.length() == 0L) {
                throw new IllegalStateException(
                    "Generated Voyager script is missing or empty: "
                        + source.getAbsolutePath()
                );
            }

            FlexAsset asset = schema.createAssetFromPath(script.path);
            voyagerFolder.add(asset);
            System.out.println("  Ingested Voyager asset: " + script.title);
            System.out.println("    path:      " + source.getAbsolutePath());
            System.out.println("    bytes:     " + source.length());
            System.out.println("    asset key: " + asset.getObjectKey());
        }
    }

    /**
     * Generates PDFs from YAML, ingests each as a FlexAsset, extracts its text,
     * embeds the extracted segments, and persists one indexed EmbeddingChunk per
     * segment. This deliberately stops before opening the index or running a query.
     */
    private void generateIngestAndEmbedDocuments(
        IFlexSchema schema,
        IFlexFolder embeddedDocs,
        List<DocumentDefinition> documents,
        EmbeddingSettings embeddingSettings,
        Map<String, ClassDefinition> classDefinitions,
        Map<String, ClassDefinition> connectionDefinitions,
        Map<String, IFlexObject> objectRegistry
    ) throws Exception {
        if (documents.isEmpty()) {
            return;
        }

        System.out.println("\n=== Generating, ingesting, extracting, and embedding PDFs ===");
        IFlexEmbeddingProviderConfig embConfig = createEmbeddingConfig(embeddingSettings);

        for (DocumentDefinition document : documents) {
            File outputFile = new File(document.path);
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException(
                    "Unable to create PDF output directory: " + parent.getAbsolutePath()
                );
            }

            generatePdf(document, outputFile);
            if (!outputFile.isFile() || outputFile.length() == 0L) {
                throw new IllegalStateException(
                    "Generated PDF is missing or empty: " + outputFile.getAbsolutePath()
                );
            }

            FlexAsset asset = schema.createAssetFromPath(document.path);
            embeddedDocs.add(asset);
            schema.save();

            System.out.println("  Generated and ingested: " + document.id);
            System.out.println("    title:     " + document.title);
            System.out.println("    path:      " + outputFile.getAbsolutePath());
            System.out.println("    bytes:     " + outputFile.length());
            System.out.println("    asset key: " + asset.getObjectKey());

            IFlexObject documentObject = createDocumentEvidenceObject(
                schema,
                document,
                asset,
                classDefinitions,
                objectRegistry
            );
            createDocumentLinks(
                schema,
                document,
                documentObject,
                connectionDefinitions,
                objectRegistry
            );
            schema.save();

            if (!embeddingSettings.enabled) {
                continue;
            }

            List<IFlexExtractionMetadata> segments;
            try (IFlexExtractionProvider extractor =
                    schema.instantiateExtractionProvider(embeddingSettings.extractionProvider)) {
                segments = extractor.extractAsset(asset.getObjectKey());
            }
            System.out.println("    extracted segments: " + segments.size());

            FlexEmbeddingResult embeddingResult;
            try (IFlexEmbeddingProvider embedder =
                    schema.instantiateEmbeddingProvider(embeddingSettings.providerName)) {
                embeddingResult = embedder.embed(segments, embConfig);
            }

            int chunkCount = 0;
            Map<ChunkLinkDefinition, Integer> chunkLinkMatches = new LinkedHashMap<>();
            for (ChunkLinkDefinition link : document.chunkLinks) {
                chunkLinkMatches.put(link, 0);
            }
            for (FlexEmbedding embedding : embeddingResult.getEmbeddings()) {
                FlexTextExtractionMetadata metadata =
                    (FlexTextExtractionMetadata) embedding.getExtractionMetadata();

                IFlexObject chunk = new FlexObject(schema, embeddingSettings.chunkClass);
                chunk.setProperty("Text", metadata.getText());
                chunk.setProperty("Vector", embedding.getVector());
                chunk.setProperty("AssetKey", asset.getObjectKey());
                chunk.setProperty("DocumentID", document.id);
                chunk.setProperty("DocumentType", document.documentType);
                chunk.setProperty("Title", document.title);
                chunk.setProperty("PageNumber", metadata.getPageNumber());
                chunk.setProperty("Section", document.section);
                chunk.save();
                chunkCount++;

                createChunkDocumentConnection(
                    schema,
                    chunk,
                    documentObject,
                    chunkCount,
                    metadata,
                    document,
                    embeddingSettings,
                    connectionDefinitions
                );
                createSeededChunkEntityConnections(
                    schema,
                    chunk,
                    metadata.getText(),
                    document,
                    connectionDefinitions,
                    objectRegistry,
                    chunkLinkMatches
                );
                chunk.save();
            }
            schema.save();
            System.out.println("    embedded chunks:   " + chunkCount);
            for (Map.Entry<ChunkLinkDefinition, Integer> entry : chunkLinkMatches.entrySet()) {
                if (entry.getValue() == 0) {
                    System.out.println("    WARNING: no chunk matched YAML phrase: "
                        + entry.getKey().matchText);
                }
            }
        }
    }

    /**
     * Creates a connection using the materialization mode declared by the
     * connection definition.
     */
    private IFlexConnection createConnection(
        IFlexSchema schema,
        IFlexObject source,
        IFlexObject target,
        String connectionName,
        ClassDefinition connectionDefinition
    ) throws Exception {
        if (connectionDefinition.isClassBasedConnection()) {
            return new FlexConnection(
                schema,
                connectionName,
                source,
                target
            );
        }
        return source.connectTo(target, connectionName);
    }

    private void createChunkDocumentConnection(
        IFlexSchema schema,
        IFlexObject chunk,
        IFlexObject documentObject,
        int sequenceNumber,
        FlexTextExtractionMetadata metadata,
        DocumentDefinition document,
        EmbeddingSettings embeddingSettings,
        Map<String, ClassDefinition> connectionDefinitions
    ) throws Exception {
        String connectionClass = embeddingSettings.chunkDocumentConnectionClass;
        ClassDefinition definition = connectionDefinitions.get(connectionClass);
        if (definition == null) {
            throw new IllegalArgumentException(
                "Undefined chunk/document connection class: " + connectionClass
            );
        }

        IFlexConnection connection = createConnection(
            schema,
            chunk,
            documentObject,
            connectionClass,
            definition
        );
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("RelationshipEstablishedAt", "2026-07-17T00:00:00Z");
        values.put("SequenceNumber", sequenceNumber);
        values.put("PageNumber", metadata.getPageNumber());
        values.put("Section", document.section);
        values.put("ExtractionProvider", embeddingSettings.extractionProvider);
        applyProperties(connection, values, definition.propertyIndex());
        connection.save();
    }

    private void createSeededChunkEntityConnections(
        IFlexSchema schema,
        IFlexObject chunk,
        String chunkText,
        DocumentDefinition document,
        Map<String, ClassDefinition> connectionDefinitions,
        Map<String, IFlexObject> objectRegistry,
        Map<ChunkLinkDefinition, Integer> matchCounts
    ) throws Exception {
        String normalizedText = chunkText == null ? "" : chunkText.toLowerCase();
        for (ChunkLinkDefinition link : document.chunkLinks) {
            if (!normalizedText.contains(link.matchText.toLowerCase())) {
                continue;
            }

            IFlexObject target = objectRegistry.get(link.to);
            if (target == null) {
                throw new IllegalArgumentException(
                    "Chunk link target does not exist: " + link.to
                );
            }
            ClassDefinition connectionDefinition = connectionDefinitions.get(
                link.connectionClass
            );
            if (connectionDefinition == null) {
                throw new IllegalArgumentException(
                    "Undefined chunk link connection class: " + link.connectionClass
                );
            }

            IFlexConnection connection = createConnection(
                schema,
                chunk,
                target,
                link.connectionClass,
                connectionDefinition
            );
            applyProperties(
                connection,
                link.properties,
                connectionDefinition.propertyIndex()
            );
            connection.save();
            matchCounts.put(link, matchCounts.get(link) + 1);
            System.out.println("      chunk evidence -> " + link.to
                + " via phrase '" + link.matchText + "'");
        }
    }

    /**
     * Creates the graph representation of an ingested PDF. The FlexAsset remains
     * the binary source of truth; this object makes document metadata and the
     * asset key available to ordinary graph traversals and connections.
     */
    private IFlexObject createDocumentEvidenceObject(
        IFlexSchema schema,
        DocumentDefinition document,
        FlexAsset asset,
        Map<String, ClassDefinition> classDefinitions,
        Map<String, IFlexObject> objectRegistry
    ) throws Exception {
        ClassDefinition classDefinition = classDefinitions.get(document.className);
        if (classDefinition == null) {
            throw new IllegalArgumentException(
                "Document '" + document.id + "' refers to undefined class '"
                    + document.className + "'."
            );
        }

        IFlexObject documentObject = schema.createObject(document.className);
        Map<String, Object> values = new LinkedHashMap<>(document.properties);
        values.put("AssetKey", String.valueOf(asset.getObjectKey()));
        values.putIfAbsent("DocumentID", document.id);
        values.putIfAbsent("Title", document.title);
        values.putIfAbsent("DocumentType", document.documentType);
        values.putIfAbsent("FilePath", document.path);

        applyProperties(
            documentObject,
            values,
            classDefinition.propertyIndex()
        );
        documentObject.save();
        objectRegistry.put(document.objectId, documentObject);

        System.out.println("    graph object: " + document.objectId
            + " as " + document.className);
        return documentObject;
    }

    /**
     * Connects the graph representation of a PDF to enterprise objects. Link
     * properties are declared in YAML and validated against the corresponding
     * FlexConnection class, exactly like ordinary object-to-object relationships.
     */
    private void createDocumentLinks(
        IFlexSchema schema,
        DocumentDefinition document,
        IFlexObject documentObject,
        Map<String, ClassDefinition> connectionDefinitions,
        Map<String, IFlexObject> objectRegistry
    ) throws Exception {
        for (DocumentLinkDefinition link : document.links) {
            IFlexObject target = objectRegistry.get(link.to);
            if (target == null) {
                throw new IllegalArgumentException(
                    "Document link target does not exist: " + link.to
                );
            }

            ClassDefinition connectionDefinition = connectionDefinitions.get(
                link.connectionClass
            );
            if (connectionDefinition == null) {
                throw new IllegalArgumentException(
                    "Document link refers to undefined connection class: "
                        + link.connectionClass
                );
            }

            IFlexConnection connection = createConnection(
                schema,
                documentObject,
                target,
                link.connectionClass,
                connectionDefinition
            );
            applyProperties(
                connection,
                link.properties,
                connectionDefinition.propertyIndex()
            );
            connection.save();
            if (connectionDefinition.isNamedConnection()) {
                documentObject.save();
            }

            System.out.println("    linked to:  " + link.to + " via "
                + link.connectionClass + " ("
                + connectionDefinition.connectionModeLabel() + ")");
        }
    }

    private void generatePdf(DocumentDefinition definition, File outputFile)
        throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            pdf.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                float margin = 54.0f;
                float y = page.getMediaBox().getHeight() - margin;

                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 18);
                content.newLineAtOffset(margin, y);
                content.showText(safePdfText(definition.title));
                content.endText();

                y -= 32.0f;
                for (String paragraph : definition.paragraphs) {
                    for (String line : wrapText(paragraph, 88)) {
                        content.beginText();
                        content.setFont(PDType1Font.HELVETICA, 11);
                        content.newLineAtOffset(margin, y);
                        content.showText(safePdfText(line));
                        content.endText();
                        y -= 15.0f;

                        if (y < margin + 24.0f) {
                            throw new IllegalArgumentException(
                                "Document is too long for the one-page factory PDF: "
                                    + definition.id
                            );
                        }
                    }
                    y -= 8.0f;
                }
            }

            pdf.save(outputFile);
        }
    }

    private static List<String> wrapText(String text, int maximumCharacters) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.trim().split("\\s+")) {
            if (current.length() > 0
                && current.length() + 1 + word.length() > maximumCharacters) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String safePdfText(String value) {
        return value
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .replace("\u201c", "\"")
            .replace("\u201d", "\"");
    }

private void applyProperties(
    IFlexObject flexObject,
    Map<String, Object> values,
    Map<String, PropertyDefinition> definitions
) throws Exception {
    for (Map.Entry<String, Object> entry : values.entrySet()) {
        PropertyDefinition definition = definitions.get(entry.getKey());
        if (definition == null) {
            throw new IllegalArgumentException(
                "YAML supplies undeclared property '" + entry.getKey() + "'."
            );
        }

        if (entry.getValue() == null) {
            System.out.println(
                "  WARNING: Omitting null optional property '"
                    + entry.getKey()
                    + "' on FlexObject."
            );
            continue;
        }

        flexObject.setProperty(
            entry.getKey(),
            convertValue(entry.getValue(), definition.type)
        );
    }
}

private void applyProperties(
    IFlexConnection connection,
    Map<String, Object> values,
    Map<String, PropertyDefinition> definitions
) throws Exception {
    for (Map.Entry<String, Object> entry : values.entrySet()) {
        PropertyDefinition definition = definitions.get(entry.getKey());
        if (definition == null) {
            throw new IllegalArgumentException(
                "YAML supplies undeclared connection property '"
                    + entry.getKey() + "'."
            );
        }

        if (entry.getValue() == null) {
            System.out.println(
                "  WARNING: Omitting null optional connection property '"
                    + entry.getKey()
                    + "'."
            );
            continue;
        }

        connection.setProperty(
            entry.getKey(),
            convertValue(entry.getValue(), definition.type)
        );
    }
}

    private static Object convertValue(Object rawValue, String declaredType) {
        if (rawValue == null) {
            throw new IllegalArgumentException(
                "Cannot convert a null value to declared type '" + declaredType
                    + "'. Optional null properties must be omitted before conversion."
            );
        }

        return switch (declaredType) {
            case "String" -> String.valueOf(rawValue);
            case "Boolean" -> rawValue instanceof Boolean value
                ? value
                : Boolean.parseBoolean(String.valueOf(rawValue));
            case "Integer" -> rawValue instanceof Number value
                ? value.intValue()
                : Integer.parseInt(String.valueOf(rawValue));
            case "Float" -> rawValue instanceof Number value
                ? value.floatValue()
                : Float.parseFloat(String.valueOf(rawValue));
            case "DateTime" -> new FlexDateTime(
                parseInstant(rawValue)
            );
            default -> throw new IllegalArgumentException(
                "Unsupported property type: " + declaredType
            );
        };
    }

    /**
     * Accept canonical ISO-8601 timestamps such as 2026-03-17T14:32:00Z.
     * For resilience, also normalize YAML timestamps written with a space and
     * explicit UTC offset, such as 2026-03-17 14:32:00+00:00.
     */
    private static Instant parseInstant(Object rawValue) {
        String value = String.valueOf(rawValue).trim();

        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException firstFailure) {
            if (value.length() > 10 && value.charAt(10) == ' ') {
                String normalized = value.substring(0, 10) + "T" + value.substring(11);
                return java.time.OffsetDateTime.parse(normalized).toInstant();
            }
            throw firstFailure;
        }
    }

    private static FlexType toFlexType(String typeName) {
        return switch (typeName) {
            case "String" -> FlexType.String;
            case "Boolean" -> FlexType.Boolean;
            case "Integer" -> FlexType.Integer;
            case "Float" -> FlexType.Float;
            case "DateTime" -> FlexType.DateTime;
            default -> throw new IllegalArgumentException(
                "Unsupported FlexVertex type in YAML: " + typeName
            );
        };
    }

    private static Map<String, ClassDefinition> indexClassDefinitions(
        List<ClassDefinition> definitions
    ) {
        Map<String, ClassDefinition> result = new LinkedHashMap<>();
        for (ClassDefinition definition : definitions) {
            if (result.put(definition.name, definition) != null) {
                throw new IllegalArgumentException(
                    "Duplicate class definition: " + definition.name
                );
            }
        }
        return result;
    }

    private void printCompletion(EnterpriseDefinition definition) {
        FlexVertexSettings fv = definition.flexvertex;
        System.out.println();
        System.out.println("============================================================");
        System.out.println(" YAML ENTERPRISE MATERIALIZED");
        System.out.println("============================================================");
        System.out.println("Data classes created:       " + definition.classes.size());
        System.out.println("Connection classes created: " + definition.connectionClasses.size());
        System.out.println("Objects created:            " + definition.objects.size());
        int documentRelationshipCount = definition.documents.stream()
            .mapToInt(document -> document.links.size())
            .sum();
        long namedRelationships = definition.relationships.stream()
            .filter(relationship -> definition.connectionClasses.stream()
                .anyMatch(connection -> connection.name.equals(relationship.connectionClass)
                    && connection.isNamedConnection()))
            .count();
        long classBasedRelationships =
            definition.relationships.size() - namedRelationships;

        System.out.println("Relationships created:      "
            + (definition.relationships.size() + documentRelationshipCount));
        System.out.println("  YAML named relationships: " + namedRelationships);
        System.out.println("  YAML class-based:         " + classBasedRelationships);
        System.out.println("PDF assets ingested:        " + definition.documents.size());
        System.out.println("PDF graph objects created:  " + definition.documents.size());
        System.out.println("Chunk/document connections: one per embedded chunk");
        int seededChunkRuleCount = definition.documents.stream()
            .mapToInt(document -> document.chunkLinks.size())
            .sum();
        System.out.println("Seeded chunk/entity rules:  " + seededChunkRuleCount);
        System.out.println("Voyager scripts generated:  " + definition.voyagerScripts.size());
        System.out.println("Voyager assets ingested:    " + definition.voyagerScripts.size());
        System.out.println("PDF extraction/embedding:   "
            + (definition.embedding.enabled ? "enabled" : "disabled"));
        System.out.println();
        System.out.println("Commander URL: http://localhost:8080");
        System.out.println("Username:      " + fv.userPath().getPath());
        System.out.println("Password:      " + fv.userPassword);
        if (!definition.voyagerScripts.isEmpty()) {
            System.out.println();
            System.out.println(
                "Generated Voyager scripts are available in the vertical's voyager directory and in Commander under Home/Voyager."
            );
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    // -------------------------------------------------------------------------
    // YAML binding model
    // -------------------------------------------------------------------------

    public static class EnterpriseDefinition {
        public FactoryMetadata factory;
        public FlexVertexSettings flexvertex;
        public List<ClassDefinition> classes;
        public List<ClassDefinition> connectionClasses;
        public List<ObjectDefinition> objects;
        public List<RelationshipDefinition> relationships;
        public List<DocumentDefinition> documents;
        public List<VoyagerScriptDefinition> voyagerScripts;
        public EmbeddingSettings embedding;
        public IconSettings icons;

        void normalize() {
            classes = safeList(classes);
            connectionClasses = safeList(connectionClasses);
            objects = safeList(objects);
            relationships = safeList(relationships);
            documents = safeList(documents);
            voyagerScripts = safeList(voyagerScripts);
            if (icons == null) {
                icons = new IconSettings();
            }
            icons.normalize();
            if (embedding == null) {
                embedding = new EmbeddingSettings();
            }
            if (factory != null) {
                factory.normalize();
            }
            if (flexvertex != null) {
                flexvertex.normalize();
            }

            for (ClassDefinition definition : classes) {
                definition.normalize();
            }
            for (ClassDefinition definition : connectionClasses) {
                definition.normalize();
            }
            for (ObjectDefinition definition : objects) {
                definition.normalize();
            }
            for (RelationshipDefinition definition : relationships) {
                definition.normalize();
            }
            for (DocumentDefinition definition : documents) {
                definition.normalize();
            }
            for (VoyagerScriptDefinition definition : voyagerScripts) {
                definition.normalize();
            }
        }

        void validate() {
            Objects.requireNonNull(factory, "factory is required");
            Objects.requireNonNull(flexvertex, "flexvertex is required");
            factory.validate();
            flexvertex.validate();
            embedding.validate();
            icons.validate(classes, embedding);
            for (VoyagerScriptDefinition script : voyagerScripts) {
                script.validate();
            }

            if (classes.isEmpty()) {
                throw new IllegalArgumentException("At least one data class is required.");
            }
            if (objects.isEmpty()) {
                throw new IllegalArgumentException("At least one object is required.");
            }

            Map<String, ClassDefinition> dataClassIndex = indexClassDefinitions(classes);
            Map<String, ClassDefinition> connectionClassIndex =
                indexClassDefinitions(connectionClasses);

            for (ClassDefinition classDefinition : classes) {
                classDefinition.validateUniqueIDProperties();
            }
            for (ClassDefinition connectionDefinition : connectionClasses) {
                connectionDefinition.validateConnectionMode();
            }

            Map<String, ObjectDefinition> objectIndex = new LinkedHashMap<>();

            for (ObjectDefinition object : objects) {
                object.validate();
                if (!dataClassIndex.containsKey(object.className)) {
                    throw new IllegalArgumentException(
                        "Object '" + object.id + "' uses undefined class '"
                            + object.className + "'."
                    );
                }
                if (objectIndex.put(object.id, object) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate YAML object id: " + object.id
                    );
                }
            }

            Map<String, DocumentDefinition> documentIndex = new LinkedHashMap<>();
            for (DocumentDefinition document : documents) {
                document.validate();
                if (documentIndex.put(document.id, document) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate YAML document id: " + document.id
                    );
                }
                if (!dataClassIndex.containsKey(document.className)) {
                    throw new IllegalArgumentException(
                        "Document '" + document.id + "' uses undefined class '"
                            + document.className + "'."
                    );
                }
                if (objectIndex.containsKey(document.objectId)) {
                    throw new IllegalArgumentException(
                        "Document object id duplicates an ordinary object id: "
                            + document.objectId
                    );
                }
                for (DocumentLinkDefinition link : document.links) {
                    if (!connectionClassIndex.containsKey(link.connectionClass)) {
                        throw new IllegalArgumentException(
                            "Undefined document connection class: "
                                + link.connectionClass
                        );
                    }
                    if (!objectIndex.containsKey(link.to)) {
                        throw new IllegalArgumentException(
                            "Undefined document link target: " + link.to
                        );
                    }
                }
                for (ChunkLinkDefinition link : document.chunkLinks) {
                    if (!connectionClassIndex.containsKey(link.connectionClass)) {
                        throw new IllegalArgumentException(
                            "Undefined chunk connection class: " + link.connectionClass
                        );
                    }
                    if (!objectIndex.containsKey(link.to)) {
                        throw new IllegalArgumentException(
                            "Undefined chunk link target: " + link.to
                        );
                    }
                }
            }

            for (RelationshipDefinition relationship : relationships) {
                relationship.validate();
                if (!connectionClassIndex.containsKey(relationship.connectionClass)) {
                    throw new IllegalArgumentException(
                        "Undefined connection class: " + relationship.connectionClass
                    );
                }
                if (!objectIndex.containsKey(relationship.from)) {
                    throw new IllegalArgumentException(
                        "Undefined relationship source: " + relationship.from
                    );
                }
                if (!objectIndex.containsKey(relationship.to)) {
                    throw new IllegalArgumentException(
                        "Undefined relationship target: " + relationship.to
                    );
                }
            }
        }
    }

    public static class IconSettings {
        public String assetFolder = "GeneratedIcons";
        public List<IconAssignment> assignments;

        void normalize() {
            assignments = safeList(assignments);
        }

        void validate(
            List<ClassDefinition> classes,
            EmbeddingSettings embedding
        ) {
            requireText(assetFolder, "icons.assetFolder");
            Set<String> classNames = new LinkedHashSet<>();
            for (ClassDefinition definition : classes) {
                classNames.add(definition.name);
            }
            // EmbeddingChunk is factory-created infrastructure rather than a
            // YAML-declared domain class, but it may still receive a class icon.
            if (embedding != null && embedding.enabled && notBlank(embedding.chunkClass)) {
                classNames.add(embedding.chunkClass);
            }
            Set<String> seen = new LinkedHashSet<>();
            for (IconAssignment assignment : assignments) {
                assignment.validate();
                if (!classNames.contains(assignment.className)) {
                    throw new IllegalArgumentException("Icon assignment refers to undefined class: " + assignment.className);
                }
                if (!seen.add(assignment.className)) {
                    throw new IllegalArgumentException("Duplicate icon assignment for class: " + assignment.className);
                }
            }
        }
    }

    public static class IconAssignment {
        public String className;
        public String path;
        public String assetName;

        void validate() {
            requireText(className, "icons.assignments.className");
            requireText(path, "icons.assignments.path");
            requireText(assetName, "icons.assignments.assetName");
        }
    }

    public static class FactoryMetadata {
        public String name;
        public String description;
        public GraphConstructionNotes graphConstruction;

        void normalize() {
            if (graphConstruction != null) {
                graphConstruction.normalize();
            }
        }

        void validate() {
            requireText(name, "factory.name");
            if (graphConstruction != null) {
                graphConstruction.validate();
            }
        }
    }

    public static class GraphConstructionNotes {
        public String demoMode;
        public List<String> realWorldMode;
        public List<String> stdoutNotes;
        public String generatedNotesPath = "GENERATED-SCENARIO-NOTES.md";

        void normalize() {
            realWorldMode = safeList(realWorldMode);
            stdoutNotes = safeList(stdoutNotes);
        }

        void validate() {
            requireText(demoMode, "factory.graphConstruction.demoMode");
            requireText(generatedNotesPath,
                "factory.graphConstruction.generatedNotesPath");
        }
    }

    public static class FlexVertexSettings {
        public String host = FlexMultiverse.getConfigString(
            null,
            "FLEX_FVS_HOST",
            "localhost"
        );
        public int port = FlexMultiverse.getConfigInteger(
            null,
            "FLEX_FVS_PORT",
            10000
        );
        public String adminPath = "/System/System/System/Admin";
        public String adminPassword = FlexMultiverse.getConfigString(
            null,
            "FLEX_FVS_ADMIN_PASSWORD",
            "Admin"
        );
        public String domain;
        public String nexus;
        public String schema;
        public String user;
        public String userPassword;
        public boolean recreateDomain = true;

        void normalize() {
            host = FlexMultiverse.getConfigString(null, "FLEX_FVS_HOST", host);
            port = FlexMultiverse.getConfigInteger(null, "FLEX_FVS_PORT", port);
            adminPassword = FlexMultiverse.getConfigString(
                null,
                "FLEX_FVS_ADMIN_PASSWORD",
                adminPassword
            );
        }

        void validate() {
            requireText(host, "flexvertex.host");
            requireText(adminPath, "flexvertex.adminPath");
            requireText(adminPassword, "flexvertex.adminPassword");
            requireText(domain, "flexvertex.domain");
            requireText(nexus, "flexvertex.nexus");
            requireText(schema, "flexvertex.schema");
            requireText(user, "flexvertex.user");
            requireText(userPassword, "flexvertex.userPassword");

            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("flexvertex.port is invalid: " + port);
            }
        }

        FlexPath userPath() {
            return new FlexPath(domain, nexus, schema, user);
        }

        String schemaPath() {
            return userPath().getDNS();
        }
    }

    public static class ClassDefinition {
        public String name;
        public String labelProperty;
        public String connectionMode;
        public List<String> uniqueIDProperties;
        public List<PropertyDefinition> properties;

        void normalize() {
            uniqueIDProperties = safeList(uniqueIDProperties);
            properties = safeList(properties);
            if (!notBlank(connectionMode)) {
                // Backward compatibility with factory YAML created before the
                // connection distinction was explicit.
                connectionMode = "named";
            }
            connectionMode = connectionMode.trim();
        }

        boolean isNamedConnection() {
            return "named".equalsIgnoreCase(connectionMode);
        }

        boolean isClassBasedConnection() {
            return "classBased".equalsIgnoreCase(connectionMode)
                || "class-based".equalsIgnoreCase(connectionMode);
        }

        String connectionModeLabel() {
            return isClassBasedConnection() ? "class-based" : "named";
        }

        void validateConnectionMode() {
            if (!isNamedConnection() && !isClassBasedConnection()) {
                throw new IllegalArgumentException(
                    "Connection class '" + name
                        + "' has unsupported connectionMode '" + connectionMode
                        + "'. Expected 'named' or 'classBased'."
                );
            }
        }

        Map<String, PropertyDefinition> propertyIndex() {
            Map<String, PropertyDefinition> result = new LinkedHashMap<>();
            for (PropertyDefinition property : properties) {
                if (result.put(property.name, property) != null) {
                    throw new IllegalArgumentException(
                        "Duplicate property '" + property.name + "' on class '" + name + "'."
                    );
                }
            }
            return result;
        }

        void validateUniqueIDProperties() {
            Map<String, PropertyDefinition> propertyIndex = propertyIndex();
            for (String propertyName : uniqueIDProperties) {
                if (!propertyIndex.containsKey(propertyName)) {
                    throw new IllegalArgumentException(
                        "Class '" + name + "' declares unknown unique-ID property '"
                            + propertyName + "'."
                    );
                }
            }
        }
    }

    public static class PropertyDefinition {
        public String name;
        public String type;
    }

    public static class ObjectDefinition {
        public String id;
        public String className;
        public Map<String, Object> properties;

        void normalize() {
            properties = safeMap(properties);
        }

        void validate() {
            requireText(id, "objects[].id");
            requireText(className, "objects[].className");
        }
    }

    public static class DocumentDefinition {
        public String id;
        public String path;
        public String title;
        public String documentType = "Generated PDF";
        public String section = "Generated content";
        public String objectId;
        public String className = "DocumentEvidence";
        public Map<String, Object> properties;
        public List<DocumentLinkDefinition> links;
        public List<ChunkLinkDefinition> chunkLinks;
        public List<String> paragraphs;

        void normalize() {
            properties = safeMap(properties);
            links = safeList(links);
            chunkLinks = safeList(chunkLinks);
            paragraphs = safeList(paragraphs);
            for (DocumentLinkDefinition link : links) {
                link.normalize();
            }
            for (ChunkLinkDefinition link : chunkLinks) {
                link.normalize();
            }
        }

        void validate() {
            requireText(id, "documents[].id");
            requireText(path, "documents[].path");
            requireText(title, "documents[].title");
            requireText(documentType, "documents[].documentType");
            requireText(section, "documents[].section");
            requireText(objectId, "documents[].objectId");
            requireText(className, "documents[].className");
            if (!path.toLowerCase().endsWith(".pdf")) {
                throw new IllegalArgumentException(
                    "documents[].path must end in .pdf: " + path
                );
            }
            if (paragraphs.isEmpty()) {
                throw new IllegalArgumentException(
                    "documents[].paragraphs must contain at least one paragraph."
                );
            }
            for (DocumentLinkDefinition link : links) {
                link.validate();
            }
            for (ChunkLinkDefinition link : chunkLinks) {
                link.validate();
            }
        }
    }

    public static class ChunkLinkDefinition {
        public String matchText;
        public String to;
        public String connectionClass = "chunkSupportsEntity";
        public Map<String, Object> properties;

        void normalize() {
            properties = safeMap(properties);
        }

        void validate() {
            requireText(matchText, "documents[].chunkLinks[].matchText");
            requireText(to, "documents[].chunkLinks[].to");
            requireText(connectionClass,
                "documents[].chunkLinks[].connectionClass");
        }
    }

    public static class DocumentLinkDefinition {
        public String to;
        public String connectionClass;
        public Map<String, Object> properties;

        void normalize() {
            properties = safeMap(properties);
        }

        void validate() {
            requireText(to, "documents[].links[].to");
            requireText(connectionClass, "documents[].links[].connectionClass");
        }
    }

    public static class VoyagerScriptDefinition {
        public String path;
        public String title;
        public String description;
        public String content;

        void normalize() {
            if (content != null) {
                content = content.strip();
            }
        }

        void validate() {
            requireText(path, "voyagerScripts[].path");
            requireText(title, "voyagerScripts[].title");
            requireText(description, "voyagerScripts[].description");
            requireText(content, "voyagerScripts[].content");
            if (!path.toLowerCase().endsWith(".voyage")) {
                throw new IllegalArgumentException(
                    "voyagerScripts[].path must end in .voyage: " + path
                );
            }
        }
    }

    public static class EmbeddingSettings {
        public boolean enabled = true;
        public String extractionProvider = "PDF";
        public String providerName = "Sentinel";
        public String chunkClass = "EmbeddingChunk";
        public String indexName = "EmbeddingChunkHybrid";
        public String chunkDocumentConnectionClass = "chunkBelongsToDocument";
        public String modelPath =
            "/models/embedding/bge-small-en-v1.5-ONNX-fp16/onnx/model_fp16.onnx";
        public String tokenizerPath =
            "/models/embedding/bge-small-en-v1.5-ONNX-fp16/tokenizer.json";
        public String configPath =
            "/models/embedding/bge-small-en-v1.5-ONNX-fp16/config.json";
        public String detectorPath =
            "/models/embedding/bge-small-en-v1.5-ONNX-fp16/en-sent.bin";
        public int maxTokens = 300;
        public int vectorDimensions = 384;

        void validate() {
            if (!enabled) {
                return;
            }
            requireText(extractionProvider, "embedding.extractionProvider");
            requireText(providerName, "embedding.providerName");
            requireText(chunkClass, "embedding.chunkClass");
            requireText(indexName, "embedding.indexName");
            requireText(chunkDocumentConnectionClass,
                "embedding.chunkDocumentConnectionClass");
            requireText(modelPath, "embedding.modelPath");
            requireText(tokenizerPath, "embedding.tokenizerPath");
            requireText(configPath, "embedding.configPath");
            requireText(detectorPath, "embedding.detectorPath");
            if (maxTokens <= 0 || vectorDimensions <= 0) {
                throw new IllegalArgumentException(
                    "embedding.maxTokens and embedding.vectorDimensions must be positive."
                );
            }
        }
    }

    public static class RelationshipDefinition {
        public String from;
        public String to;
        public String connectionClass;
        public Map<String, Object> properties;

        void normalize() {
            properties = safeMap(properties);
        }

        void validate() {
            requireText(from, "relationships[].from");
            requireText(to, "relationships[].to");
            requireText(connectionClass, "relationships[].connectionClass");
        }
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? new ArrayList<>() : value;
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : value;
    }

    private static void requireText(String value, String path) {
        if (!notBlank(value)) {
            throw new IllegalArgumentException(path + " is required.");
        }
    }
}
