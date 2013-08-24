/**
 *  Copyright 2013 OpenBEL Consortium
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.openbel.framework.tools;

import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.getenv;
import static java.lang.System.out;
import static org.openbel.framework.common.BELUtilities.asPath;
import static org.openbel.framework.common.BELUtilities.hasLength;
import static org.openbel.framework.common.BELUtilities.isNumeric;
import static org.openbel.framework.common.BELUtilities.noLength;
import static org.openbel.framework.common.cfg.SystemConfiguration.createSystemConfiguration;
import static org.openbel.framework.core.StandardOptions.LONG_OPT_DEBUG;
import static org.openbel.framework.core.StandardOptions.SHORT_OPT_VERBOSE;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.cli.Option;
import org.openbel.framework.api.DefaultDialect;
import org.openbel.framework.api.DefaultSpeciesDialect;
import org.openbel.framework.api.KAMStore;
import org.openbel.framework.api.KAMStoreImpl;
import org.openbel.framework.api.Kam;
import org.openbel.framework.api.Kam.KamEdge;
import org.openbel.framework.api.Kam.KamNode;
import org.openbel.framework.api.KamDialect;
import org.openbel.framework.api.KamSpecies;
import org.openbel.framework.api.internal.KAMCatalogDao.KamInfo;
import org.openbel.framework.api.internal.KAMStoreDaoImpl.BelTerm;
import org.openbel.framework.common.SimpleOutput;
import org.openbel.framework.common.cfg.SystemConfiguration;
import org.openbel.framework.compiler.kam.KAMStoreSchemaService;
import org.openbel.framework.compiler.kam.KAMStoreSchemaServiceImpl;
import org.openbel.framework.core.CommandLineApplication;
import org.openbel.framework.core.df.DBConnection;
import org.openbel.framework.core.df.DatabaseService;
import org.openbel.framework.core.df.DatabaseServiceImpl;
import org.openbel.framework.core.df.encryption.KamStoreEncryptionServiceImpl;
import org.openbel.framework.tools.XGMMLObjects.Edge;
import org.openbel.framework.tools.XGMMLObjects.Node;
import org.openbel.framework.tools.pkam.DefaultPKAMSerializationService;
import org.openbel.framework.tools.pkam.PKAMSerializationFailure;
import org.openbel.framework.tools.pkam.PKAMSerializationService;

/**
 * ExportOrthology orthologizes a KAM from the {@link KAMStore} and exports it
 * as either XGMML (graph format) or KAM (portal KAM file) format.
 */
public class ExportOrthology extends CommandLineApplication {

    // environment constants
    private static final String ENV_BELFRAMEWORK_HOME = "BELFRAMEWORK_HOME";
    private static final String CMD_HOME = "CMD_HOME";
    private static final String CONFIG_DIRECTORY = "config";
    private static final String BELFRAMEWORK_CONFIG = "belframework.cfg";

    private final boolean verbose;
    private final boolean debug;

    private KAMStore kamStore;

    private ExportOrthology(String[] args) {
        super(args);

        verbose = hasOption(SHORT_OPT_VERBOSE);
        debug = hasOption(LONG_OPT_DEBUG);

        final SimpleOutput reportable = new SimpleOutput();
        reportable.setErrorStream(err);
        reportable.setOutputStream(out);
        setReportable(reportable);

        if (hasOption('h')) {
            printHelp(true);
            exit(0);
        }
    }

    public void run() {
        if (!hasOption("k")) {
            printUsage();
            fatal("The KAM name was not provided.");
        }
        if (!hasOption("s")) {
            printUsage();
            fatal("The species taxonomy id was not provided.");
        }
        if (!hasOption("t")) {
            printUsage();
            fatal("The export type was not provided.");
        }

        String kamName = getOptionValue("k");
        String taxValue = getOptionValue("s");
        if (!isNumeric(taxValue)) {
            printUsage();
            fatal("The species taxonomy id is not a number.");
        }
        int taxId = Integer.parseInt(taxValue);
        String exportType = getOptionValue("t");
        boolean xgmml = false;
        if ("XGMML".equalsIgnoreCase(exportType)) {
            xgmml = true;
        } else if ("KAM".equalsIgnoreCase(exportType)) {
            xgmml = false;
        } else {
            printUsage();
            fatal(format("The export type '%s' is not supported." +
            		"Provide either %s or %s.", exportType, "XGMML", "KAM"));
        }

        try {
            setUp();
        } catch (IOException e) {
            fatal(e.getMessage());
        } catch (SQLException e) {
            fatal(e.getMessage());
        }

        Kam kam = kamStore.getKam(kamName);
        if (kam == null) {
            fatal(format("The specified KAM '%s' cannot be found.", kamName));
            return;
        }

        KamInfo info = kam.getKamInfo();
        kam = new KamSpecies(new KamDialect(kam, new DefaultDialect(info,
                kamStore, false)), new DefaultSpeciesDialect(
                kam.getKamInfo(), kamStore, taxId, false), kamStore);

        String fileName = kamName + (xgmml ? ".xgmml" : ".kam");
        File file = new File(fileName);

        if (verbose || debug) {
            reportable.output(format("Loaded KAM '%s' and orthologized to %d",
                    kamName, taxId));
            reportable.output(format("Exporting KAM '%s' to file '%s'.", kamName, file));
        }

        if (xgmml) {
            try {
                writeXGMML(kam, file);
            } catch (IOException e) {
                fatal(e.getMessage());
            }
        } else {
            DatabaseService dbs = new DatabaseServiceImpl();
            KamStoreEncryptionServiceImpl es = new KamStoreEncryptionServiceImpl();
            KAMStoreSchemaService ss = new KAMStoreSchemaServiceImpl(dbs);

            PKAMSerializationService pkam = new DefaultPKAMSerializationService(
                    dbs, es, ss);
            try {
                pkam.serializeKAM(kamName, fileName, null);
            } catch (PKAMSerializationFailure e) {
                fatal(e.getMessage());
            }
        }
        reportable.output(format("Saved file '%s'",
                file.getAbsolutePath()));
    }

    /**
     * Obtain the {@link SystemConfiguration}.<br>
     * Defaults to obtaining via the BELFRAMEWORK_HOME environment variable.
     *
     * @return
     * @throws Exception
     */
    private SystemConfiguration getSystemConfiguration() throws IOException {
        final String bfhome = getenv(ENV_BELFRAMEWORK_HOME);
        if (hasLength(bfhome)) {
            return createSystemConfiguration();
        }

        String cmdHome = getenv(CMD_HOME);

        // assert that CMD_HOME is set, alert the user
        assert noLength(cmdHome);
        if (noLength(cmdHome)) {
            throw new IllegalStateException("CMD_HOME needs to be set.");
        }

        String cfgPath = asPath(getenv(CMD_HOME), CONFIG_DIRECTORY,
                BELFRAMEWORK_CONFIG);
        return createSystemConfiguration(new File(cfgPath));
    }

    private void setUp() throws IOException, SQLException {
        SystemConfiguration cfg = getSystemConfiguration();
        if (verbose || debug) {
            reportable.output("Using KAM Store URL: " + cfg.getKamURL());
            reportable.output("Using KAM Store User: " + cfg.getKamUser());
        }
        DatabaseService dbs = new DatabaseServiceImpl();
        DBConnection dbc = dbs.dbConnection(
                cfg.getKamURL(),
                cfg.getKamUser(),
                cfg.getKamPassword());
        kamStore = new KAMStoreImpl(dbc);

        if (verbose || debug) {
            reportable.output("Accessed the KAM store.");
        }
    }

    private void writeXGMML(final Kam kam, final File outf) throws IOException {
        // Set up a writer to write the XGMML
        PrintWriter writer = new PrintWriter(outf);

        // Write xgmml <graph> element header
        XGMMLUtility.writeStart("Species-specific KAM for "
                + kam.getKamInfo().getName(), writer);

        // Iterate over the path nodes and capture in XGMML
        final Collection<KamNode> nodes = kam.getNodes();
        for (KamNode pathNode : nodes) {
            Node xNode = new Node();
            xNode.id = pathNode.getId();
            xNode.label = pathNode.getLabel();
            xNode.function = pathNode.getFunctionType();

            List<BelTerm> supportingTerms = kamStore
                    .getSupportingTerms(pathNode);

            XGMMLUtility.writeNode(xNode, supportingTerms, writer);
        }

        // Iterate over the path nodes, find the edges, and capture in XGMML
        for (KamEdge edge : kam.getEdges()) {
            Edge xEdge = new Edge();
            xEdge.id = edge.getId();
            xEdge.rel = edge.getRelationshipType();
            xEdge.source = edge.getSourceNode().getId();
            xEdge.target = edge.getTargetNode().getId();

            KamNode knsrc = edge.getSourceNode();
            KamNode kntgt = edge.getTargetNode();

            Node src = new Node();
            src.function = knsrc.getFunctionType();
            src.label = knsrc.getLabel();
            Node tgt = new Node();
            tgt.function = kntgt.getFunctionType();
            tgt.label = kntgt.getLabel();

            XGMMLUtility.writeEdge(src, tgt, xEdge, writer);
        }

        // Close out the writer
        XGMMLUtility.writeEnd(writer);
        writer.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getApplicationDescription() {
        return "Exports an orthologized KAM.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getApplicationName() {
        return "Export Orthology";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getApplicationShortName() {
        return "Export Orthology";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Option> getCommandLineOptions() {
        List<Option> options = new ArrayList<Option>();
        options.add(new Option("k", "kam", true, "the KAM name"));
        options.add(new Option("s", "taxid", true,
                "the NCBI species taxonomy id"));
        options.add(new Option("t", "type", true,
                "the export type (XGMML or KAM)"));
        return options;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUsage() {
        StringBuilder bldr = new StringBuilder();
        bldr.append(" -k <kam>");
        bldr.append(" -s <tax-id>");
        bldr.append(" -t <XGMML or KAM>");
        return bldr.toString();
    }

    /**
     * Launch the {@link ExportOrthology} command.
     *
     * @param args {@link String[]} command-line arguments
     */
    public static void main(String[] args) {
        ExportOrthology tool = new ExportOrthology(args);
        tool.run();
    }
}
