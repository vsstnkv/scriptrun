//CREATE REMOTE ISSUE AND TWO-WAY LINK
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.CustomFieldManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.IssueManager
import com.atlassian.jira.security.groups.GroupManager
import com.atlassian.jira.user.ApplicationUser
import com.onresolve.scriptrunner.runner.customisers.PluginModule
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectFacade
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.IQLFacade
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.ObjectTypeAttributeFacade
import com.riadalabs.jira.plugins.insight.services.model.factory.ObjectAttributeBeanFactory
import com.riadalabs.jira.plugins.insight.services.model.ObjectBean
import com.riadalabs.jira.plugins.insight.channel.external.api.facade.ConfigureFacade
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.commons.httpclient.methods.StringRequestEntity
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.Header
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpClient
import org.apache.log4j.Level
import org.apache.log4j.Logger


//TEST ISSUE
//MutableIssue issue = ComponentAccessor.getIssueManager().getIssueByCurrentKey("")


JSMcreateRemoteTask script = new JSMcreateRemoteTask()
script.run(issue)


class JSMcreateRemoteTask {
    Logger log = Logger.getLogger(this.class)
    @WithPlugin("com.riadalabs.jira.plugins.insight")
    @PluginModule ObjectFacade objectFacade
    @PluginModule IQLFacade iqlFacade
    @PluginModule ObjectTypeAttributeFacade objectTypeAttributeFacade
    @PluginModule ObjectAttributeBeanFactory objectAttributeBeanFactory
    @PluginModule ConfigureFacade configureFacade
    IssueManager issueManager = ComponentAccessor.getIssueManager()
    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager()
    GroupManager groupManager = ComponentAccessor.getGroupManager()
    ApplicationUser sysUser = ComponentAccessor.getUserManager().getUserByName('username')
    ApplicationUser curUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    String baseUrl = ComponentAccessor.getApplicationProperties().getString("jira.baseurl")
    String jiraLink = "https://baseurl.ru"
    String helpToJiraLinkId = "linkid"
    String jiraToHelpLinkId = "linkid2"


    final String auKey = "Basic auKey"

//Check issue type and catch errors    
    void run (MutableIssue issue){
        log.setLevel(Level.DEBUG)
        long scriptStartTime = System.currentTimeMillis()
        log.debug("${issue.getKey()} Script started")


        String jsmkey = issue.getKey()
        if (issue.getIssueType().getName() in ["IssutTypeName"]){
            try {
            ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(sysUser)
            Map createdMap = createRemoteIssue(issue)
                if (createdMap){
                    createTwoWayRemoteLinkFromHelpToJira(issue, Long.parseLong(createdMap.newIssueId.toString()), createdMap.newIssueKey.toString())
                }
            } catch (e) {
                log.debug("""${issue.getKey()}: error ${e}""")
            } finally {
                ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(curUser)
            }
        }


        long scriptWorkTime = System.currentTimeMillis() - scriptStartTime
        log.debug("${issue.getKey()} Script work time: ${scriptWorkTime} ms.")
        }

//Create remote issue through API
    Map createRemoteIssue (MutableIssue issue) {
        Map result = [:]
        String jsmkey = issue.getKey()
        boolean toUpdate = false


        if (true){
            CustomField error = customFieldManager.getCustomFieldObject(11111)  //Id of Customfield
            String errorType = issue.getCustomFieldValue(error).toString()
            String description = issue.getDescription().toString() 


            String textForIssue = "Ссылка на задачу : https://baseurl.ru/browse/${jsmkey}\nТип ошибки: ${errorType}\n Описание: ${description} \n"


            Map a = [
                fields: [
                    project: [
                        key: "JIRAPROJ"
                    ],
                    summary: "Задача из JSM",
                    description: textForIssue,
                    issuetype: [
                        name: "Task"
                    ]
                ]
            ]
            JsonBuilder json = new JsonBuilder(a)

            String URL = "${jiraLink}/rest/api/latest/issue/"
            StringRequestEntity requestEntity = new StringRequestEntity(json.toString(),"application/json","UTF-8")
            PostMethod postMethod = new PostMethod(URL)
            postMethod.addRequestHeader(new Header("Authorization", "Basic ${auKey}"))
            postMethod.addRequestHeader(new Header("Content-Type", "application/json"))
            postMethod.setRequestEntity(requestEntity)
            final HttpClient httpClient = new HttpClient()
            try {
                Integer answerCode = httpClient.executeMethod(postMethod)
                log.debug("Post answer code: ${answerCode}")
                if (answerCode==201) {
                    String responseBody = postMethod?.getResponseBodyAsString()
                    JsonSlurper slurper = new JsonSlurper()
                    def bodyMap = slurper.parseText(responseBody)
                    assert bodyMap instanceof Map
                    result.newIssueKey = bodyMap.key
                    result.newIssueId = bodyMap.id
                }
            } catch (e) {
                log.debug("""error ${e}""")
            }
        } else {
            log.debug("error")
        }
        return result
    }

//CREATE TWO-WAY LINK BETWEEN TASKS
    void createTwoWayRemoteLinkFromHelpToJira (MutableIssue issue, Long remoteIssueId, String remoteIssueKey){
        log.setLevel(Level.DEBUG)
        String issueKey = issue.getKey()
        Long issueId = issue.getId()


        Map from = [
            globalId: "appId=${helpToJiraLinkId}&issueId=${remoteIssueId}",
            application: [
                type: "com.atlassian.jira",
                name: "Jira"
            ],
            relationship: "is cloned by",
            object: [
                url: "${jiraLink}/browse/${remoteIssueKey}",
                title: "${remoteIssueKey}"
            ]
        ]


        JsonBuilder jsonFrom = new JsonBuilder(from)
        StringRequestEntity fromRequestEntity = new StringRequestEntity(jsonFrom.toString(),"application/json","UTF-8")
        String urlFrom = "${baseUrl}/rest/api/latest/issue/${issueKey}/remotelink"
        PostMethod fromPostMethod = new PostMethod(urlFrom)
        fromPostMethod.addRequestHeader(new Header("Authorization", "Basic ${auKey}"))
        fromPostMethod.addRequestHeader(new Header("Content-Type", "application/json"))
        fromPostMethod.setRequestEntity(fromRequestEntity)
        final HttpClient fromHttpClient = new HttpClient()
        try {
            Integer toCode = fromHttpClient.executeMethod(fromPostMethod)
            log.debug("toCode answer code: ${toCode}")
        } catch(e) {
            log.debug("""error ${e}""")
        }


        Map to = [
            globalId: "appId=${jiraToHelpLinkId}&issueId=${issueId}",
            application: [
                type: "com.atlassian.jira",
                name: "HelpDesk"
            ],
            relationship: "clones",
            object: [
                url: "${baseUrl}/browse/${issueKey}",
                title: "${issueKey}"
            ]
        ]


        JsonBuilder jsonTo = new JsonBuilder(to)
        StringRequestEntity toRequestEntity = new StringRequestEntity(jsonTo.toString(),"application/json","UTF-8")
        String urlTo= "${jiraLink}/rest/api/latest/issue/${remoteIssueKey}/remotelink"
        PostMethod toPostMethod = new PostMethod(urlTo)
        toPostMethod.addRequestHeader(new Header("Authorization", "Basic ${auKey}"))
        toPostMethod.addRequestHeader(new Header("Content-Type", "application/json"))
        toPostMethod.setRequestEntity(toRequestEntity)
        final HttpClient toHttpClient = new HttpClient()
        try {
            Integer toCode = toHttpClient.executeMethod(toPostMethod)
            log.debug("fromCode answer code: ${toCode}")
        } catch(e) {
            log.debug("""error ${e}""")
        }
    }
}
