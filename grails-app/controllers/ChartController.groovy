import com.recomdata.export.ExportTableNew
import grails.converters.JSON
import org.jfree.chart.servlet.ChartDeleter
import org.jfree.chart.servlet.ServletUtilities
import org.transmart.searchapp.AccessLog
import org.transmart.searchapp.AuthUser
import org.transmartproject.core.users.User

import javax.servlet.ServletException
import javax.servlet.ServletOutputStream
import javax.servlet.http.HttpSession

class ChartController {

    def index = {}

    def i2b2HelperService
    def springSecurityService
    def chartService
    def accessLogService
    User currentUserBean
    def highDimensionQueryService


    def displayChart = {
        HttpSession session = request.getSession();
        String filename = request.getParameter("filename");
        log.trace("Trying to display:" + filename)
        if (filename == null) {
            throw new ServletException("Parameter 'filename' must be supplied");
        }

        //  Replace ".." with ""
        //  This is to prevent access to the rest of the file system
        filename = ServletUtilities.searchReplace(filename, "..", "");

        //  Check the file exists
        File file = new File(System.getProperty("java.io.tmpdir"), filename);
        if (!file.exists()) {
            throw new ServletException("File '" + file.getAbsolutePath()
                    + "' does not exist");
        }

        //  Check that the graph being served was created by the current user
        //  or that it begins with "public"
        boolean isChartInUserList = false;
        ChartDeleter chartDeleter = (ChartDeleter) session.getAttribute(
                "JFreeChart_Deleter");
        if (chartDeleter != null) {
            isChartInUserList = chartDeleter.isChartAvailable(filename);
        }

        boolean isChartPublic = false;
        if (filename.length() >= 6) {
            if (filename.substring(0, 6).equals("public")) {
                isChartPublic = true;
            }
        }

        boolean isOneTimeChart = false;
        if (filename.startsWith(ServletUtilities.getTempOneTimeFilePrefix())) {
            isOneTimeChart = true;
        }

        //if (isChartInUserList || isChartPublic || isOneTimeChart) {
        /*Code change by Jeremy Isikoff, Recombinant Inc. to always serve up images*/

        //  Serve it up
        ServletUtilities.sendTempFile(file, response);
        return;
    }

    /**
     * Action to get the counts for the children of the passed in concept key
     */
    def childConceptPatientCounts = {

        def paramMap = params;

        def user = AuthUser.findByUsername(springSecurityService.getPrincipal().username)
        def concept_key = params.concept_key;
        def counts = i2b2HelperService.getChildrenWithPatientCountsForConcept(concept_key)
        def access = i2b2HelperService.getChildrenWithAccessForUserNew(concept_key, user)

        def obj = [counts: counts, accesslevels: access]
        render obj as JSON
    }

    /**
     * Action to get the patient count for a concept
     */
    def conceptPatientCount = {
        String concept_key = params.concept_key;
        PrintWriter pw = new PrintWriter(response.getOutputStream());
        pw.write(i2b2HelperService.getPatientCountForConcept(concept_key).toString());
        pw.flush();
    }

    /**
     * Action to get the distribution histogram for a concept
     */
    def conceptDistribution = {

        // Lets put a bit of 'audit' in here
        new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Set Value Concept Histogram", eventmessage: "Concept:" + params.concept_key, accesstime: new java.util.Date()).save()

        // We retrieve the result instance ids from the client
        def concept = params.concept_key ?: null

        // We retrieve the highdimension parameters from the client, if they were passed
        def omics_params = [:]
        params.findAll { k, v ->
            k ==~ /omics_/
        }.each { k, v ->
            omics_params[k] = v
        }

        // Collect concept information
        // We need to force computation for an empty instance ID
        concept = chartService.getConceptAnalysis(concept: i2b2HelperService.getConceptKeyForAnalysis(concept), omics_params: omics_params, subsets: [ 1: [ exists: true, instance : "" ], 2: [ exists: false ], commons: [:]], chartSize : [width : 245, height : 180])

        PrintWriter pw = new PrintWriter(response.getOutputStream());
        pw.write(concept.commons.conceptHisto)
        pw.flush();
    }


    def conceptDistributionForSubset = {

        // Lets put a bit of 'audit' in here
        new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Set Value Concept Histogram for subset", eventmessage: "Concept:" + params.concept_key, accesstime: new java.util.Date()).save()

        // We retrieve the result instance ids from the client
        def concept = params.concept_key ?: null

        // We retrieve the highdimension parameters from the client, if they were passed
        def omics_params = [:]
        params.findAll { k, v ->
            k ==~ /omics_/
        }.each { k, v ->
            omics_params[k] = v
        }

        // Collect concept information
        concept = chartService.getConceptAnalysis(concept: i2b2HelperService.getConceptKeyForAnalysis(concept), omics_params: omics_params, subsets: chartService.getSubsetsFromRequest(params), chartSize : [width : 245, height : 180])

        PrintWriter pw = new PrintWriter(response.getOutputStream());
        pw.write(concept.commons.conceptHisto)
        pw.flush();
    }

<<<<<<< HEAD
    /**
     * Action to get the basic statistics for the subset comparison and render them
     */
    def basicStatistics = {

        request.getSession().setAttribute("gridtable", null);

        def result_instance_id1 = params.result_instance_id1;
        def result_instance_id2 = params.result_instance_id2;
        def al = new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Basic Statistics", eventmessage: "RID1:" + result_instance_id1 + " RID2:" + result_instance_id2, accesstime: new java.util.Date())

        al.save()

        def boolean s1 = true;
        def boolean s2 = true;
        if (result_instance_id1 == "" || result_instance_id1 == null) {s1 = false;}
        if (result_instance_id2 == "" || result_instance_id2 == null) {s2 = false;}

        PrintWriter pw = new PrintWriter(response.getOutputStream());

        pw.write("<html><head><link rel='stylesheet' type='text/css' href='${resource(dir: 'css', file: 'chartservlet.css')}'></head><body><div class='analysis'>");
        pw.write("<table width='100%'>");
        pw.write("<tr><td colspan='2' align='center'><div class='analysistitle' id='analysis_title'>Summary " +
                "Statistics</div></td></tr>");
        pw.write("<tr><td width='50%' align='center'>");
        if (s1) {
            i2b2HelperService.renderQueryDefinition(result_instance_id1, "Query Summary for Subset 1", pw);
        }
        pw.write("</td><td align='center'>");
        if (s2) {
            i2b2HelperService.renderQueryDefinition(result_instance_id2, "Query Summary for Subset 2", pw);
        }
        pw.write("</tr>");
        pw.write("<tr><td colspan='2' align='center'>");
		
        renderPatientCountInfoTable(result_instance_id1, result_instance_id2, pw);

        /*get the data*/
        double[] values3 = s1 ? i2b2HelperService.getPatientDemographicValueDataForSubset("AGE_IN_YEARS_NUM", result_instance_id1) : []
        double[] values4 = s2 ? i2b2HelperService.getPatientDemographicValueDataForSubset("AGE_IN_YEARS_NUM", result_instance_id2) : []

        log.trace("Rendering age histograms")
        /*render the double histogram*/
        HistogramDataset dataset3 = new HistogramDataset();
        if (s1) {
            dataset3.addSeries("Subset 1", values3, 10, StatHelper.min(values3), StatHelper.max(values3));
        }
        if (s2) {
            dataset3.addSeries("Subset 2", values4, 10, StatHelper.min(values4), StatHelper.max(values4));
        }
        JFreeChart chart3 = ChartFactory.createHistogram(
                "Histogram of Age",
                null,
                "Count",
                dataset3,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        chart3.getTitle().setFont(new Font("SansSerif", Font.BOLD, 12));
        XYPlot plot3 = (XYPlot) chart3.getPlot();
        plot3.setForegroundAlpha(0.85f);

        XYBarRenderer renderer3 = (XYBarRenderer) plot3.getRenderer();
        renderer3.setDrawBarOutline(false);
        // flat bars look best...
        renderer3.setBarPainter(new StandardXYBarPainter());
        renderer3.setShadowVisible(false);

        NumberAxis rangeAxis3 = (NumberAxis) plot3.getRangeAxis();
        rangeAxis3.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        NumberAxis domainAxis3 = (NumberAxis) plot3.getDomainAxis();
        //domainAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        ChartRenderingInfo info3 = new ChartRenderingInfo(new StandardEntityCollection());

        String filename3 = ServletUtilities.saveChartAsJPEG(chart3, 245, 180, info3, request.getSession());
        String graphURL3 = request.getContextPath() + "/chart/displayChart?filename=" + filename3;
        pw.write("</td></tr><tr><td colspan=2 align='center'><table><tr>");
        pw.write("<td><img src='" + graphURL3 + "' width=245 height=180 border=0 usemap='#" + filename3 + "'>");
        ChartUtilities.writeImageMap(pw, filename3, info3, false);
        pw.write("</td>");

        /*Render the box plot*/
        DefaultBoxAndWhiskerCategoryDataset dataset7 = new DefaultBoxAndWhiskerCategoryDataset();

        ArrayList<Number> l1 = new ArrayList<Number>();
        for (int i = 0; i < values3.length; i++) {
            l1.add(values3[i]);
        }
        ArrayList<Number> l2 = new ArrayList<Number>();
        for (int i = 0; i < values4.length; i++) {
            l2.add(values4[i]);
        }
        BoxAndWhiskerItem boxitem1 = BoxAndWhiskerCalculator.calculateBoxAndWhiskerStatistics(l1);
        BoxAndWhiskerItem boxitem2 = BoxAndWhiskerCalculator.calculateBoxAndWhiskerStatistics(l2);
        if (s1 && l1.size() > 0) {
            dataset7.add(boxitem1, "Series 1", "Subset 1");
        }
        if (s2 && l2.size() > 0) {
            dataset7.add(boxitem2, "Series 2", "Subset 2");
        }

        JFreeChart chart7 = ChartFactory.createBoxAndWhiskerChart(
                "Comparison of Age", "", "Value", dataset7,
                false);
        chart7.getTitle().setFont(new Font("SansSerif", Font.BOLD, 12));
        CategoryPlot plot7 = (CategoryPlot) chart7.getPlot();
        plot7.setDomainGridlinesVisible(true);

        NumberAxis rangeAxis7 = (NumberAxis) plot7.getRangeAxis();
        BoxAndWhiskerRenderer rend7 = (BoxAndWhiskerRenderer) plot7.getRenderer();
        rend7.setMaximumBarWidth(0.10);
        //rangeAxis7.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        ChartRenderingInfo info7 = new ChartRenderingInfo(new StandardEntityCollection());

        String filename7 = ServletUtilities.saveChartAsJPEG(chart7, 200, 300, info7, request.getSession());
        String graphURL7 = request.getContextPath() + "/chart/displayChart?filename=" + filename7;
        pw.write("<td align='center'>");
        if (s1 && l1.size() > 0) {
            pw.write("<div class='smalltitle'><b>Subset 1</b></div>");
            renderBoxAndWhiskerInfoTable(l1, pw);
        }
        pw.write("</td>");
        pw.write("<td><img src='" + graphURL7 + "' width=200 height=300 border=0 usemap='#" + filename7 + "'>");
        //String rmodulesVersion = grailsApplication.mainContext.pluginManager.getGrailsPlugin('rdc-rmodules').version;
		String rmodulesVersion = "1";
        pw.write("<td valign='top'><div style='position:relative;left:-30px;'><a  href=\"javascript:showInfo('plugins/rdc-rmodules-$rmodulesVersion/help/boxplot.html');\"><img src=\"../images/information.png\"></a></div></td>");
        //Should be dynamic to plugin!
        pw.write("</td><td align='center'>");
        if (s2 && l2.size() > 0) {
            pw.write("<div class='smalltitle'><b>Subset 2</b></div>");
            renderBoxAndWhiskerInfoTable(l2, pw);
        }
        ChartUtilities.writeImageMap(pw, filename7, info7, false);
        pw.write("</td></tr></table>");
        pw.write("</td></tr><tr><td width='50%' align='center'>");

        if (s1) {
            HashMap<String, Integer> sexs1 = i2b2HelperService.getPatientDemographicDataForSubset("sex_cd", result_instance_id1);
            JFreeChart chart = createConceptAnalysisPieChart(hashMapToPieDataset(sexs1, "Sex"), "Sex");
            info7 = new ChartRenderingInfo(new StandardEntityCollection());
            filename7 = ServletUtilities.saveChartAsJPEG(chart, 200, 200, info7, request.getSession());
            graphURL7 = request.getContextPath() + "/chart/displayChart?filename=" + filename7;
            pw.write("<img src='" + graphURL7 + "' width=200 height=200 border=0 usemap='#" + filename7 + "'>");
            ChartUtilities.writeImageMap(pw, filename7, info7, false);
            //pw.write("<b>Sex</b>");
            renderCategoryResultsHashMap(sexs1, "Subset 1", i2b2HelperService.getPatientSetSize(result_instance_id1), pw);
=======
    def conceptDistributionWithValues = {
        // Lets put a bit of 'audit' in here
        new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Concept Distribution With Values", eventmessage: "Concept:" + params.concept_key, accesstime: new java.util.Date()).save()

        def concept = params.concept_key ?: null

        // We retrieve the highdimension parameters from the client, if they were passed
        def omics_params = [:]
        params.findAll { k, v ->
            k.startsWith("omics_")
        }.each { k, v ->
            omics_params[k] = v
>>>>>>> 3a25179a7c09c8daf43343721df3aab3f9550e7b
        }
        // Collect concept information
        concept = chartService.getConceptAnalysis(concept: i2b2HelperService.getConceptKeyForAnalysis(concept), omics_params: omics_params, subsets: [ 1: [ exists: true, instance : "" ], 2: [ exists: false ], commons: [:]], chartSize : [width : 245, height : 180])

        render concept as JSON
    }

    /**
     * Gets an analysis for a concept key and comparison
     */
    def analysis = {

        // Lets put a bit of 'audit' in here
        new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Analysis by Concept", eventmessage: "RID1:" + params.result_instance_id1 + " RID2:" + params.result_instance_id2 + " Concept:" + params.concept_key, accesstime: new java.util.Date()).save()

        // We retrieve the result instance ids from the client
        def concept = params.concept_key ?: null
        def concepts = [:]


        // We retrieve the highdimension parameters from the client, if they were passed
        def omics_params = [:]
        params.findAll { k, v ->
            k.startsWith("omics_")
        }.each { k, v ->
            omics_params[k] = v
        }
        // We add the key to our cache set
        chartService.keyCache.add(concept)

        // Collect concept information
        concepts[concept] = chartService.getConceptAnalysis(concept: i2b2HelperService.getConceptKeyForAnalysis(concept), omics_params: omics_params, subsets: chartService.getSubsetsFromRequest(params))

        // Time to delivery !
        render(template: "conceptsAnalysis", model: [concepts: concepts])
    }

    /**
     * Action to get the basic statistics for the subset comparison and render them
     */
    def basicStatistics = {

        // Lets put a bit of 'audit' in here
        new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Basic Statistics", eventmessage: "RID1:" + params.result_instance_id1 + " RID2:" + params.result_instance_id2, accesstime: new java.util.Date()).save()

        // We clear the keys in our cache set
        chartService.keyCache.clear()

        // This clears the current session grid view data table and key cache
        request.session.setAttribute("gridtable", null);

        // We retrieve all our charts from our ChartService
        def subsets = chartService.computeChartsForSubsets(chartService.getSubsetsFromRequest(params))
        def concepts = chartService.getConceptsForSubsets(subsets)
        concepts.putAll(chartService.getHighDimensionalConceptsForSubsets(subsets))

        // Time to delivery !
        render(template: "summaryStatistics", model: [subsets: subsets, concepts: concepts])
    }

    def analysisGrid = {

        String concept_key = params.concept_key;
        def result_instance_id1 = params.result_instance_id1;
        def result_instance_id2 = params.result_instance_id2;

        /*which subsets are present? */
        boolean s1 = (result_instance_id1 == "" || result_instance_id1 == null) ? false : true;
        boolean s2 = (result_instance_id2 == "" || result_instance_id2 == null) ? false : true;

        def al = new AccessLog(username: springSecurityService.getPrincipal().username, event: "DatasetExplorer-Grid Analysis Drag", eventmessage: "RID1:" + result_instance_id1 + " RID2:" + result_instance_id2 + " Concept:" + concept_key, accesstime: new java.util.Date())
        al.save()

        //XXX: session is a questionable place to store this because it breaks multi-window/tab nav
        ExportTableNew table = (ExportTableNew) request.getSession().getAttribute("gridtable");
        if (table == null) {

            table = new ExportTableNew();
            if (s1) i2b2HelperService.addAllPatientDemographicDataForSubsetToTable(table, result_instance_id1, "subset1");
            if (s2) i2b2HelperService.addAllPatientDemographicDataForSubsetToTable(table, result_instance_id2, "subset2");

            List<String> keys = i2b2HelperService.getConceptKeysInSubsets(result_instance_id1, result_instance_id2);
            Set<String> uniqueConcepts = i2b2HelperService.getDistinctConceptSet(result_instance_id1, result_instance_id2);

            log.debug("Unique concepts: " + uniqueConcepts);
            log.debug("keys: " + keys)

            for (int i = 0; i < keys.size(); i++) {

                if (!i2b2HelperService.isHighDimensionalConceptKey(keys.get(i))) {
                    log.trace("adding concept data for " + keys.get(i));
                    if (s1) i2b2HelperService.addConceptDataToTable(table, keys.get(i), result_instance_id1);
                    if (s2) i2b2HelperService.addConceptDataToTable(table, keys.get(i), result_instance_id2);
                }
            }

            def highDimConcepts = highDimensionQueryService.getHighDimensionalConceptSet(result_instance_id1, result_instance_id2)
            highDimConcepts.each {
                if (s1) highDimensionQueryService.addHighDimConceptDataToTable(table, it, result_instance_id1)
                if (s2) highDimensionQueryService.addHighDimConceptDataToTable(table, it, result_instance_id2)
            }
        }
        PrintWriter pw = new PrintWriter(response.getOutputStream());

        if (concept_key && !concept_key.isEmpty()) {
            // We retrieve the highdimension parameters from the client, if they were passed
            def omics_params = [:]
            params.findAll { k, v ->
                k.startsWith("omics_")
            }.each { k, v ->
                omics_params[k] = v
            }
            if (omics_params) {
                omics_params.concept_key = concept_key
                if (s1) highDimensionQueryService.addHighDimConceptDataToTable(table, omics_params, result_instance_id1)
                if (s2) highDimensionQueryService.addHighDimConceptDataToTable(table, omics_params, result_instance_id2)
            }
            else {
                String parentConcept = i2b2HelperService.lookupParentConcept(i2b2HelperService.keyToPath(concept_key));
                Set<String> cconcepts = i2b2HelperService.lookupChildConcepts(parentConcept, result_instance_id1, result_instance_id2);

                def conceptKeys = [];
                def prefix = concept_key.substring(0, concept_key.indexOf("\\", 2));

                if (!cconcepts.isEmpty()) {
                    for (cc in cconcepts) {
                        def ck = prefix + i2b2HelperService.getConceptPathFromCode(cc);
                        conceptKeys.add(ck);
                    }
                } else
                    conceptKeys.add(concept_key);

                for (ck in conceptKeys) {
                    if (s1) i2b2HelperService.addConceptDataToTable(table, ck, result_instance_id1);
                    if (s2) i2b2HelperService.addConceptDataToTable(table, ck, result_instance_id2);
                }
            }

        }
        pw.write(table.toJSONObject().toString(5));
        pw.flush();

        request.getSession().setAttribute("gridtable", table);
    }

    def reportGridTableExport() {

        ExportTableNew gridTable = request.session.gridtable

        def exportedVariablesCsv = gridTable.columnMap.entrySet()
                .collectAll { "${it.value.label} (id = ${it.key})" }.join(', ')

        def trialsCsv = gridTable.rows
                .collectAll { it['TRIAL'] }.unique().join(', ')

        accessLogService.report(currentUserBean, 'Grid View Data Export',
                eventMessage: "User (IP: ${request.getHeader('X-FORWARDED-FOR') ?: request.remoteAddr}) just exported" +
                        " data for trial(s) (${trialsCsv}): variables (${exportedVariablesCsv}) measurements for the" +
                        " following patient set(s): " +
                        [params.result_instance_id1, params.result_instance_id2].findAll().join(', '),
                requestURL: request.forwardURI)

        render 'ok'
    }

    def clearGrid = {
        log.debug("Clearing grid");
        request.getSession().setAttribute("gridtable", null);
        log.debug("Setting export filename to null, since there is nothing to export")
        request.getSession().setAttribute("expdsfilename", null);
        PrintWriter pw = new PrintWriter(response.getOutputStream());
        response.setContentType("text/plain");
        pw.write("grid cleared!");
        pw.flush();
    }


    def exportGrid = {
        byte[] bytes = ((ExportTableNew) request.getSession().getAttribute("gridtable")).toCSVbytes();
        int outputSize = bytes.length;
        //response.setContentType("application/vnd.ms-excel");
        response.setContentType("text/csv");
        response.setHeader("Content-disposition", "attachment; filename=" + "export.csv");
        response.setContentLength(outputSize);
        ServletOutputStream servletoutputstream = response.getOutputStream();
        servletoutputstream.write(bytes);
        servletoutputstream.flush();
    }
}

