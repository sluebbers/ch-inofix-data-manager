package ch.inofix.data.web.internal.portlet.action;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.io.FileUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.liferay.document.library.kernel.exception.FileSizeException;
import com.liferay.document.library.kernel.service.DLFileEntryLocalService;
import com.liferay.exportimport.kernel.configuration.ExportImportConfigurationConstants;
import com.liferay.exportimport.kernel.exception.LARFileException;
import com.liferay.exportimport.kernel.exception.LARFileSizeException;
import com.liferay.exportimport.kernel.exception.LARTypeException;
import com.liferay.exportimport.kernel.exception.LayoutImportException;
import com.liferay.exportimport.kernel.lar.ExportImportHelper;
import com.liferay.exportimport.kernel.lar.ExportImportHelperUtil;
import com.liferay.exportimport.kernel.model.ExportImportConfiguration;
import com.liferay.exportimport.kernel.service.ExportImportConfigurationLocalService;
import com.liferay.exportimport.kernel.staging.StagingUtil;
import com.liferay.portal.kernel.exception.LayoutPrototypeException;
import com.liferay.portal.kernel.exception.LocaleException;
import com.liferay.portal.kernel.exception.NoSuchBackgroundTaskException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONFactoryUtil;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.service.LayoutService;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadException;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.upload.UploadRequestSizeException;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.ContentTypes;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.StreamUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import ch.inofix.data.constants.PortletKeys;
import ch.inofix.data.service.MeasurementService;
import ch.inofix.data.web.configuration.ExportImportConfigurationSettingsMapFactory;

/**
 * 
 * @author Christian Berndt
 * @created 2017-11-01 17:20
 * @modified 2017-12-17 23:19
 * @version 1.0.6
 *
 */
@Component(
    immediate = true,
    property = {
        "javax.portlet.name=" + PortletKeys.DATA_MANAGER,
        "mvc.command.name=importMeasurements"
    },
    service = MVCActionCommand.class
)
public class ImportMeasurementsMVCActionCommand extends BaseMVCActionCommand {

    protected void addTempFileEntry(ActionRequest actionRequest, String folderName) throws Exception {

        _log.info("addTempFileEntry()");

        UploadPortletRequest uploadPortletRequest = _portal.getUploadPortletRequest(actionRequest);

        checkExceededSizeLimit(uploadPortletRequest);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");

        deleteTempFileEntry(groupId, folderName);

        InputStream inputStream = null;

        try {
            String sourceFileName = uploadPortletRequest.getFileName("file");

            inputStream = uploadPortletRequest.getFileAsStream("file");

            String contentType = uploadPortletRequest.getContentType("file");

            _layoutService.addTempFileEntry(groupId, folderName, sourceFileName, inputStream, contentType);
        } catch (Exception e) {
            UploadException uploadException = (UploadException) actionRequest.getAttribute(WebKeys.UPLOAD_EXCEPTION);

            if (uploadException != null) {
                Throwable cause = uploadException.getCause();

                if (cause instanceof FileUploadBase.IOFileUploadException) {
                    if (_log.isInfoEnabled()) {
                        _log.info("Temporary upload was cancelled");
                    }
                }

                if (uploadException.isExceededFileSizeLimit()) {
                    throw new FileSizeException(cause);
                }

                if (uploadException.isExceededUploadRequestSizeLimit()) {
                    throw new UploadRequestSizeException(cause);
                }
            } else {
                throw e;
            }
        } finally {
            StreamUtil.cleanUp(inputStream);
        }
    }

    protected void checkExceededSizeLimit(HttpServletRequest request) throws PortalException {

        _log.info("checkExceededSizeLimit()");

        UploadException uploadException = (UploadException) request.getAttribute(WebKeys.UPLOAD_EXCEPTION);

        if (uploadException != null) {
            Throwable cause = uploadException.getCause();

            if (uploadException.isExceededFileSizeLimit() || uploadException.isExceededUploadRequestSizeLimit()) {

                throw new LARFileSizeException(cause);
            }

            throw new PortalException(cause);
        }
    }
    
    protected void deleteBackgroundTasks(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {
        
        _log.info("deleteBackgroundTasks()");

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
        long groupId = themeDisplay.getScopeGroupId();

        try {
            long[] backgroundTaskIds = ParamUtil.getLongValues(actionRequest, "deleteBackgroundTaskIds");

            for (long backgroundTaskId : backgroundTaskIds) {
                _measurementService.deleteBackgroundTask(groupId, backgroundTaskId);
            }
        } catch (Exception e) {
            if (e instanceof NoSuchBackgroundTaskException || e instanceof PrincipalException) {

                SessionErrors.add(actionRequest, e.getClass());

                actionResponse.setRenderParameter("mvcPath", "/error.jsp");

                hideDefaultSuccessMessage(actionRequest);

            } else {
                throw e;
            }
        }

        addSuccessMessage(actionRequest, actionResponse);

    }

    protected void deleteTempFileEntry(ActionRequest actionRequest, ActionResponse actionResponse, String folderName)
            throws Exception {

        _log.info("deleteTempFileEntry()");

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        JSONObject jsonObject = JSONFactoryUtil.createJSONObject();

        try {
            String fileName = ParamUtil.getString(actionRequest, "fileName");

            _layoutService.deleteTempFileEntry(themeDisplay.getScopeGroupId(), folderName, fileName);

            jsonObject.put("deleted", Boolean.TRUE);
        } catch (Exception e) {
            String errorMessage = themeDisplay.translate("an-unexpected-error-occurred-while-deleting-the-file");

            jsonObject.put("deleted", Boolean.FALSE);
            jsonObject.put("errorMessage", errorMessage);
        }

        JSONPortletResponseUtil.writeJSON(actionRequest, actionResponse, jsonObject);
    }

    protected void deleteTempFileEntry(long groupId, String folderName) throws PortalException {

        _log.info("deleteTempFileEntry()");

        String[] tempFileNames = _layoutService.getTempFileNames(groupId, folderName);

        for (String tempFileEntryName : tempFileNames) {
            _layoutService.deleteTempFileEntry(groupId, folderName, tempFileEntryName);
        }
    }

    @Override
    protected void doProcessAction(ActionRequest actionRequest, ActionResponse actionResponse) throws Exception {

        _log.info("doProcessAction()");

        String cmd = ParamUtil.getString(actionRequest, Constants.CMD);

        _log.info("cmd = " + cmd);

        String redirect = ParamUtil.getString(actionRequest, "redirect");

        try {
            if (cmd.equals(Constants.ADD_TEMP)) {

                addTempFileEntry(actionRequest, ExportImportHelper.TEMP_FOLDER_NAME);
                validateFile(actionRequest, actionResponse, ExportImportHelper.TEMP_FOLDER_NAME);
                hideDefaultSuccessMessage(actionRequest);

            } else if (cmd.equals(Constants.DELETE)) {

                deleteBackgroundTasks(actionRequest, actionResponse);

                sendRedirect(actionRequest, actionResponse, redirect);

            } else if (cmd.equals(Constants.DELETE_TEMP)) {

                deleteTempFileEntry(actionRequest, actionResponse, ExportImportHelper.TEMP_FOLDER_NAME);
                hideDefaultSuccessMessage(actionRequest);

            } else if (cmd.equals(Constants.IMPORT)) {

                importData(actionRequest, ExportImportHelper.TEMP_FOLDER_NAME);

                sendRedirect(actionRequest, actionResponse, redirect);
            
            } else if (cmd.equals("importDataFromURL")) {
                
                _log.info("importDataFromURL");

                importDataFromURL(actionRequest, ExportImportHelper.TEMP_FOLDER_NAME);

                sendRedirect(actionRequest, actionResponse, redirect);

            }
        } catch (Exception e) {
            if (cmd.equals(Constants.ADD_TEMP) || cmd.equals(Constants.DELETE_TEMP)) {

                hideDefaultSuccessMessage(actionRequest);
                handleUploadException(actionRequest, actionResponse, ExportImportHelper.TEMP_FOLDER_NAME, e);

            } else {

                // TODO: remove dependencies to LARFile*
                if (e instanceof LARFileException || e instanceof LARFileSizeException
                        || e instanceof LARTypeException) {

                    SessionErrors.add(actionRequest, e.getClass());
                } else if (e instanceof LayoutPrototypeException || e instanceof LocaleException) {

                    SessionErrors.add(actionRequest, e.getClass(), e);
                } else {
                    _log.error(e, e);

                    SessionErrors.add(actionRequest, LayoutImportException.class.getName());
                }
            }
        }
    }

    protected void handleUploadException(ActionRequest actionRequest, ActionResponse actionResponse, String folderName,
            Exception e) throws Exception {

        _log.info("handleUploadException()");

        HttpServletResponse response = _portal.getHttpServletResponse(actionResponse);

        response.setContentType(ContentTypes.TEXT_HTML);
        response.setStatus(HttpServletResponse.SC_OK);

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        deleteTempFileEntry(themeDisplay.getScopeGroupId(), folderName);

        JSONObject jsonObject = StagingUtil.getExceptionMessagesJSONObject(themeDisplay.getLocale(), e,
                (ExportImportConfiguration) null);

        JSONPortletResponseUtil.writeJSON(actionRequest, actionResponse, jsonObject);
    }

    protected void importData(ActionRequest actionRequest, String folderName) throws Exception {

        _log.info("importData()");

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");

        FileEntry fileEntry = ExportImportHelperUtil.getTempFileEntry(groupId, themeDisplay.getUserId(), folderName);

        InputStream inputStream = null;

        try {
            inputStream = _dlFileEntryLocalService.getFileAsStream(fileEntry.getFileEntryId(), fileEntry.getVersion(),
                    false);

            importData(actionRequest, fileEntry.getTitle(), inputStream);

            deleteTempFileEntry(groupId, folderName);
        } finally {
            StreamUtil.cleanUp(inputStream);
        }
    }

    protected void importData(ActionRequest actionRequest, String fileName, InputStream inputStream) throws Exception {

        _log.info("importData()");

        String extension = FileUtil.getExtension(fileName);

        _log.info("extension = " + extension);

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");

        Map<String, Serializable> importMeasurementSettingsMap = ExportImportConfigurationSettingsMapFactory
                .buildImportMeasurementsSettingsMap(themeDisplay.getUserId(), groupId, actionRequest.getParameterMap(),
                        themeDisplay.getLocale(), themeDisplay.getTimeZone());

        // TODO: reconsider addDraft...
        // TODO: reconsider TYPE_IMPORT_LAYOUT
        ExportImportConfiguration exportImportConfiguration = _exportImportConfigurationLocalService
                .addDraftExportImportConfiguration(themeDisplay.getUserId(),
                        ExportImportConfigurationConstants.TYPE_IMPORT_LAYOUT, importMeasurementSettingsMap);

        _measurementService.importMeasurementsInBackground(exportImportConfiguration, inputStream, extension);

    }
    
    protected void importDataFromURL(ActionRequest actionRequest, String folderName) throws Exception {

        String dataURL = ParamUtil.getString(actionRequest, "dataURL");
        String password = ParamUtil.getString(actionRequest, "password");
        String userName = ParamUtil.getString(actionRequest, "userName");

        String extension = dataURL.substring(dataURL.lastIndexOf(".") + 1, dataURL.length());

        File file = FileUtil.createTempFile(extension);

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        final String user = userName;
        final String pw = password;
        
        if (Validator.isNotNull(password) && Validator.isNotNull(userName)) {

            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, pw.toCharArray());
                }
            });
        }

        URL url = new URL(dataURL);

        String fileName = url.getFile();

        FileUtils.copyURLToFile(url, file);

        InputStream inputStream = null;

        try {

            inputStream = new FileInputStream(file);

            importData(actionRequest, fileName, inputStream);

            deleteTempFileEntry(themeDisplay.getScopeGroupId(), folderName);

        } finally {
            StreamUtil.cleanUp(inputStream);
        }

    }

    @Reference(unbind = "-")
    protected void setDLFileEntryLocalService(DLFileEntryLocalService dlFileEntryLocalService) {

        _dlFileEntryLocalService = dlFileEntryLocalService;
    }

    @Reference(unbind = "-")
    protected void setExportImportConfigurationLocalService(
            ExportImportConfigurationLocalService exportImportConfigurationLocalService) {

        _exportImportConfigurationLocalService = exportImportConfigurationLocalService;
    }

    @Reference(unbind = "-")
    protected void setLayoutService(LayoutService layoutService) {
        _layoutService = layoutService;
    }

    @Reference(unbind = "-")
    protected void setMeasurementService(MeasurementService measurementService) {
        _measurementService = measurementService;
    }

    protected void validateFile(ActionRequest actionRequest, ActionResponse actionResponse, String folderName)
            throws Exception {

        _log.info("validateFile()");

        ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

        long groupId = ParamUtil.getLong(actionRequest, "groupId");

        FileEntry fileEntry = ExportImportHelperUtil.getTempFileEntry(groupId, themeDisplay.getUserId(), folderName);

        InputStream inputStream = null;

        try {
            inputStream = _dlFileEntryLocalService.getFileAsStream(fileEntry.getFileEntryId(), fileEntry.getVersion(),
                    false);

            // TODO: validate uploaded file

            JSONObject jsonObject = JSONFactoryUtil.createJSONObject();
            //
            // if ((weakMissingReferences != null) &&
            // !weakMissingReferences.isEmpty()) {
            //
            // jsonObject.put("warningMessages",
            // StagingUtil.getWarningMessagesJSONArray(themeDisplay.getLocale(),
            // weakMissingReferences));
            // }

            JSONPortletResponseUtil.writeJSON(actionRequest, actionResponse, jsonObject);
        } finally {
            StreamUtil.cleanUp(inputStream);
        }
    }

    private static Log _log = LogFactoryUtil.getLog(ImportMeasurementsMVCActionCommand.class.getName());

    private DLFileEntryLocalService _dlFileEntryLocalService;
    private ExportImportConfigurationLocalService _exportImportConfigurationLocalService;
    private LayoutService _layoutService;
    private MeasurementService _measurementService;

    @Reference
    private Portal _portal;
}
