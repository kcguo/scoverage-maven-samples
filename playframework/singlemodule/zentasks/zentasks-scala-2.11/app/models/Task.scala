package models

import java.sql.Connection
import java.util.Date

import javax.inject.Inject
import javax.inject.Singleton

import play.api.db._

import anorm._
import anorm.SqlParser._

import scala.language.postfixOps

case class Task(id: Option[Long], folder: String, project: Long, title: String, done: Boolean, dueDate: Option[Date], assignedTo: Option[String])

@Singleton
class TaskService @Inject() (dbapi: DBApi, projectService: ProjectService) {
  
  private val db = dbapi.database("default")

  // -- Parsers
  
  /**
   * Parse a Task from a ResultSet
   */
  val simple = {
    get[Option[Long]]("task.id") ~
    get[String]("task.folder") ~
    get[Long]("task.project") ~
    get[String]("task.title") ~
    get[Boolean]("task.done") ~
    get[Option[Date]]("task.due_date") ~
    get[Option[String]]("task.assigned_to") map {
      case id~folder~project~title~done~dueDate~assignedTo => Task(
        id, folder, project, title, done, dueDate, assignedTo
      )
    }
  }
  
  // -- Queries
  
  /**
   * Retrieve a Task from the id.
   */
  def findById(id: Long): Option[Task] = {
    db.withConnection { implicit connection =>
      SQL("select * from task where id = {id}").on(
        "id" -> id
      ).as(simple.singleOpt)
    }
  }
  
  /**
   * Retrieve todo tasks for the user.
   */
  def findTodoInvolving(user: String): Seq[(Task,Project)] = {
    db.withConnection { implicit connection =>
      SQL(
        """
          select * from task 
          join project_member on project_member.project_id = task.project 
          join project on project.id = project_member.project_id
          where task.done = false and project_member.user_email = {email}
        """
      ).on(
        "email" -> user
      ).as(simple ~ projectService.simple map {
        case task~project => task -> project
      } *)
    }
  }
  
  /**
   * Find tasks related to a project
   */
  def findByProject(project: Long): Seq[Task] = {
    db.withConnection { implicit connection =>
      SQL(
        """
          select * from task 
          where task.project = {project}
        """
      ).on(
        "project" -> project
      ).as(simple *)
    }
  }

  /**
   * Delete a task
   */
  def delete(id: Long) {
    db.withConnection { implicit connection =>
      SQL("delete from task where id = {id}").on(
        "id" -> id
      ).executeUpdate()
    }
  }
  
  /**
   * Delete all task in a folder.
   */
  def deleteInFolder(projectId: Long, folder: String) {
    db.withConnection { implicit connection =>
      SQL("delete from task where project = {project} and folder = {folder}").on(
        "project" -> projectId, "folder" -> folder
      ).executeUpdate()
    }
  }
  
  /**
   * Mark a task as done or not
   */
  def markAsDone(taskId: Long, done: Boolean) {
    db.withConnection { implicit connection =>
      SQL("update task set done = {done} where id = {id}").on(
        "id" -> taskId,
        "done" -> done
      ).executeUpdate()
    }
  }
  
  /**
   * Rename a folder.
   */
  def renameFolder(projectId: Long, folder: String, newName: String) {
    db.withConnection { implicit connection =>
      SQL("update task set folder = {newName} where folder = {name} and project = {project}").on(
        "project" -> projectId, "name" -> folder, "newName" -> newName
      ).executeUpdate()
    }
  }
  
  /**
   * Check if a user is the owner of this task
   */
  def isOwner(task: Long, user: String): Boolean = {
    db.withConnection { implicit connection =>
      SQL(
        """
          select count(task.id) = 1 from task 
          join project on task.project = project.id 
          join project_member on project_member.project_id = project.id 
          where project_member.user_email = {email} and task.id = {task}
        """
      ).on(
        "task" -> task,
        "email" -> user
      ).as(scalar[Boolean].single)
    }
  }

  /**
   * Create a Task.
   */
  def create(task: Task): Task = {
    db.withConnection { implicit connection =>
      
      // Get the task id
      val id: Long = task.id.getOrElse {
        SQL("select next value for task_seq").as(scalar[Long].single)
      }
      
      SQL(
        """
          insert into task values (
            {id}, {title}, {done}, {dueDate}, {assignedTo}, {project}, {folder}
          )
        """
      ).on(
        "id" -> id,
        "folder" -> task.folder,
        "project" -> task.project,
        "title" -> task.title,
        "done" -> task.done,
        "dueDate" -> task.dueDate,
        "assignedTo" -> task.assignedTo
      ).executeUpdate()
      
      task.copy(id = Some(id))
    }
  }
  
}
