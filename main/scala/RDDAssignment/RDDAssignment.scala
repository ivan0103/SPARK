package RDDAssignment

import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Timestamp
import java.util.UUID
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import utils.{Commit, File, Stats}

/**
 * Hint regarding the exercises: it is sometimes specified that the assignment asks about the committer or the
 * commit author. Those are two different entities, as per the Commit.scala file. Inspect it thoroughly and make
 * sure to always refer to the proper entity!
 */
object RDDAssignment {

  //in 6,7,8
  //intHelper = 5 (repo name)
  //intHelper = 4 (committer name)
  def helperCut(stringHelper: String, intHelper: Int): String = {
    val helperArray = stringHelper.split("/").reverse.reverse.drop(intHelper)
    helperArray.reverse.last
  }

  //in 7
  def helperRevert(input: Array[String]): Int = {

    if (input.length == 0) {
      return 0
    }

    if (input.reverse.last != "Revert") {
      return 0
    }

    return 1 + helperRevert(input.reverse.reverse.drop(1))
  }

  //9
  def helperDate(xDate: Timestamp, yDate: Timestamp): Timestamp = {

    if (xDate.before(yDate)) {
      return xDate
    }

    return yDate
  }


  /**
   * Description
   *
   * Reductions are often used in data processing in order to gather more useful data out of raw data. In this case
   * we want to know how many commits a given RDD contains.
   *
   *
   * Hints
   *
   * You should be able to complete this assignment with using only one function. If in doubt, read the Spark RDD
   * documentation in detail: https://spark.apache.org/docs/2.4.3/api/scala/index.html#org.apache.spark.rdd.RDD
   *
   * @param commits RDD containing commit data.
   * @return Long indicating the number of commits in the given RDD.
   */
  def assignment_1(commits: RDD[Commit]): Long = commits.count()

  /**
   * Description
   *
   * We want to know what is the most popular email domain.
   * We require a RDD containing tuples of the used
   *        - email domain
   *        - combined with the number of occurrences.
   *
   * Hints
   *
   * You should use the email of the author
   *
   * @param commits RDD containing commit data.
   * @return RDD containing tuples indicating the email domain (extension) and number of occurrences.
   */
  def assignment_2(commits: RDD[Commit]): RDD[(String, Long)] = {
    val helper1 = commits.
      map(x => x.commit.author.email).distinct().
      map(x => (x.split("@").last, 1)).reduceByKey((acc, x) => acc + x).map(x => (x._1, x._2.asInstanceOf[Long]))

    helper1
  }

  /**
   * Description
   *
   * Return a Tuple with :
   *      - filename
   *      - number of changes of the most frequently changed file
   *        If there is no filename, use 'unknown'.
   *
   * Hints
   *
   * Files in a directory must have unique names but can have the same name in different directories.
   * During refactoring, files can be moved between directories directories, resulting in the same file
   * having a different absolute path from a point in time. However, a directory can have more than
   * one file with the same name (but in different directories), so just taking the file name might be too lenient.
   * To simplify things, you may assume that an absolute path is sufficient to identify a file. To further simplify this,
   * use absolute filepath as filename.
   *
   * @param commits RDD containing commit data.
   * @return A tuple containing the filename and number of changes.
   */
  def assignment_3(commits: RDD[Commit]): (String, Long) = {
    val helper1 = (commits.map(x1 => x1.files.map(x2 => (x2.filename, x2.changes)))).flatMap(x => x)

    val helper2 = helper1.groupBy(_._1).mapValues(_.map(_._2).sum).sortBy(_._2, false)

    val helper3 = helper2.first()

    if (helper3._1.isEmpty) {
      return ("unknown", helper3._2)
    }

    return (helper3._1.get, helper3._2)
    /* commits.map(x => x.files).flatMap(identity)
      .map(x=> (x.filename.toString, x.changes)).sortBy(_._2).first().

     */
  }

  /**
   * Description
   *
   * Some users on Github might be interested in their ranking in number of comments.
   * Return a RDD containing tuples of:
   *        - the rank (zero indexed) of a commit author
   *        - commit author's name
   *        - the sum of comments counts made by the commit author.
   *
   * As in general with performance rankings, a higher performance means a better
   * ranking (0 = best). In case of a tie, the lexicographical ordering of the usernames should be used to break the
   * tie. For the lexicographical ordering, the size of the letters should not matter.
   *
   * @param commits RDD containing commit data.
   * @return RDD containing the rank number, commit author names and number of comments of author in order.
   */
  def assignment_4(commits: RDD[Commit]): RDD[(Long, String, Long)] = {
    val authorComments = commits
      .map(commit => (commit.commit.author.name, commit.commit.comment_count))
      .reduceByKey(_ + _)

    val sortedAuthors = authorComments
      .map { case (name, count) => ((-count, name.toLowerCase), name, count) }
      .sortBy(x => x._1)

    val rankedAuthors = sortedAuthors
      .zipWithIndex()
      .map { case (((_, _), name, count), rank) => (rank, name, count) }

    rankedAuthors
  }

  /**
   * Description
   *
   * We want to know how stable and how widely used some programming languages are.
   * There are many ways to achieve that,but for the purpose of this exercise,
   * the measure we choose is how many additions, deletions and changes occur in each file extension.
   * We will provide a list of file extensions.
   *
   * We want an RDD of tuples containing :
   *        - file extension name
   *        - Stats object. (Stats object is only used for commits and single files only have additions,
   *          deletions and changes value)
   *
   * we want you to compose the Stats object for each file with those values.
   *
   * Hints
   *
   * The value of "changes" is the sum of additions and deletions, so it is an equivalent of the
   * "total" value in stats.
   *
   * @param commits        RDD containing commit data.
   * @param fileExtensions List of String containing file extensions
   * @return RDD containing file extension and an aggregation of the committers' Stats.
   */
  def assignment_5(commits: RDD[Commit], fileExtensions: List[String]): RDD[(String, Stats)] = {

    // Step 1: Map and filter to get file extension and stats values.
    val fileStats = commits
      .flatMap(commit => commit.files)
      .filter(file => file.filename.isDefined)
      .filter(file => {
        val extension = file.filename.get.split("\\.").last
        fileExtensions.contains(extension)
      })
      .map(file => (file.filename.get.split("\\.").last, (file.additions, file.deletions, file.additions + file.deletions)))

    // Step 2: Reduce by key to aggregate stats.
    val aggregatedStats = fileStats
      .reduceByKey((x, y) => (x._1 + y._1, x._2 + y._2, x._3 + y._3))

    // Step 3: Map to the desired output format.
    val result = aggregatedStats.map { case (extension, (additions, deletions, changes)) =>
      (extension, Stats(changes, additions, deletions))
    }

    result
  }

  /**
   * Description
   *
   * There are different types of people, those who own repositories, and those who make commits.
   * There are also people who do both.
   *
   * We require as output an RDD containing :
   *      - names of commit authors and repository owners that have both committed to repositories
   *        and own repositories in the given RDD.
   *
   * Note that the repository owner is contained within Github urls.
   *
   * @param commits RDD containing commit data.
   * @return RDD of Strings representing the author names that have both committed to and own repositories.
   */
  def assignment_6(commits: RDD[Commit]): RDD[String] = {
    // Extract a set of users who have committed to repositories
    val commitAuthors = commits
      .filter(commit => commit.commit.author.name.nonEmpty)
      .map(commit => commit.commit.author.name)
      .distinct()

    // Extract a set of users who own repositories from GitHub URLs
    val repositoryOwners = commits
      .map(commit => helperCut(commit.url, 4))
      .distinct()

    // Find the intersection of commit authors and repository owners
    val result = commitAuthors.intersection(repositoryOwners)

    result
  }

  // Function to extract the owner from a GitHub URL

  /** IMPORTANT NOTE!!!!!!
   *
   * * From here on, expensive functions on RDDs like groupBy are *NOT* allowed.
   * In real life wide dependency functions are performance killers, but luckily there are better performing
   * alternatives! Automatic graders will check computation history of returned RDDs.
   */


  /**
   * Description
   *
   *
   * Sometimes developers make mistakes, sometimes they make many. One way of observing mistakes in commits is by
   * looking at so-called revert commits. We define a 'revert streak' as the number of times `Revert` occurs
   * in a commit.
   *
   * Note that for a commit to be eligible for a 'commit streak', its message must start with `Revert`.
   * As an example: `Revert "Revert ...` would be a revert streak of 2, whilst `Oops, Revert Revert little mistake`
   * is not a 'revert streak'.
   *
   * Return a RDD containing tuples of
   *      - repository name (can be derived from the url)
   *      - average streak length computed over all commits.
   *
   * Note: Commit messages such as 'Reverted...' or 'Revert removal of "..."' also count for the revert streak.
   *
   * @param commits RDD containing commit data.
   * @return RDD of Tuple type containing a repository name and a double representing the average streak length.
   */
  def assignment_7(commits: RDD[Commit]): RDD[(String, Double)] = {
    commits
      .flatMap(commit => {
        val repoName = helperCut(commit.url, 5)
        val isRevert = isRevertStreak(commit.commit.message)
        if (isRevert) {
          Some((repoName, 1.0))
        } else {
          Some((repoName, 0.0))
        }
      })
      .aggregateByKey((0.0, 0))(
        (acc, value) => (acc._1 + value, acc._2 + 1),
        (acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2)
      )
      .mapValues { case (totalStreaks, commitCount) =>
        totalStreaks / commitCount
      }
  }

  // Function to check if a commit message is part of a revert streak
  def isRevertStreak(message: String): Boolean = {
    message.startsWith("Revert")
  }

  /**
   *
   * Description
   *
   * We want to know the number of commits that are made by unique committers (represented by the field committer
   * in CommitData) in a given RDD. Besides the number of commits, we also want to know how many different
   * repositories the committers committed to. The repository name can be found in url.
   *
   * @param commits RDD containing commit data.
   * @return RDD of tuple containing committer name, list of repositories and
   *         total number of commits committed across all repositories.
   */
  def assignment_8(commits: RDD[Commit]): RDD[(String, Iterable[String], Long)] = {

    val helper1 = commits.map(x => (x.commit.committer.name, (helperCut(x.url, 5), 1)))

    val helper2 = helper1.reduceByKey((x, y) => (x._1 + " " + y._1, x._2 + y._2))

    val helper3 = helper2.map(x => (x._1, x._2._1.split(" ").distinct.toList.toIterable, x._2._2.asInstanceOf[Long]))

    helper3
  }

  /**
   * Description
   *
   * Return RDD of tuples containing
   *  - repository names
   *  - list of all commit authors of that repository (commit.author.name), with date of first commit.
   *
   *
   * Hint
   * Use commit.author.date
   *
   * @param commits RDD containing commit data.
   * @return RDD containing the repository names, list of tuples of Timestamps and commit author names
   */
  def assignment_9(commits: RDD[Commit]): RDD[(String, Iterable[(Timestamp, String)])] = {

    val helper1 = commits.map(x => ((helperCut(x.url, 5), x.commit.author.name), x.commit.author.date)).sortBy(_._1._1)

    val helper2 = helper1.reduceByKey((x, y) => helperDate(x, y))

    val helper3 = helper2.map(x => (x._1._1, List((x._2, x._1._2))))

    val helper4 = helper3.reduceByKey((x, y) => x ++ y)

    helper4.map(x => (x._1, x._2.toIterable))
  }

  /**
   * Description
   *
   * We want to know the committers that worked on a certain file to make an overview of every file in a repository.
   *
   * Create a tuple containing
   *  - file name
   *  - set of tuples with name of committers
   *  - Stat object representing the changes made to the file by each committer.
   *
   * @param commits    RDD containing commit data.
   * @param repository String name of repository
   * @return RDD containing tuples representing a file name and a list of tuples of committer names and Stats object.
   */
  def assignment_10(commits: RDD[Commit], repository: String): RDD[(String, List[(String, Stats)])] = {
    val fileCommittersStats = commits
      .filter(commit => helperCut(commit.url, 5) == repository)
      .flatMap(commit => {
        commit.files.map(file => {
          val filename = file.filename.getOrElse("unknown")
          val committerName = commit.commit.committer.name
          val stats = Stats(file.changes, file.additions, file.deletions)
          ((filename, committerName), stats)
        })
      })

    val combinedStats = fileCommittersStats
      .reduceByKey((stats1, stats2) => Stats(
        stats1.total + stats2.total,
        stats1.additions + stats2.additions,
        stats1.deletions + stats2.deletions
      ))

    val result = combinedStats
      .map { case ((filename, committerName), stats) =>
        (filename, List((committerName, stats)))
      }
      .reduceByKey((list1, list2) => list1 ++ list2)

    result
  }
  /**
   *
   * Hashing function that computes the md5 hash from a String, which in terms returns a Long to act as a hashing
   * function for repository name and username.
   *
   * @param s String to be hashed, consecutively mapped to a Long.
   * @return Long representing the MSB from the inputted String.
   */
  def md5HashString(s: String): Long = {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(s.getBytes)
    val bigInt = new BigInteger(1, digest)
    val hashedString = bigInt.toString(16)
    UUID.nameUUIDFromBytes(hashedString.getBytes()).getMostSignificantBits
  }

  /**
   * Create a bi-directional graph from committer to repositories. Use md5HashString function above to create unique
   * identifiers for creating a graph.
   *
   * As the real usage Sparks GraphX library is out of the scope of this course, we will not go further into this, but
   * this can be used for algorithms like PageRank, Hubs and Authorities, clique finding, etc.
   *
   * We expect a node for each repository and each committer (based on committer name), an edge from each
   * committer to repositories the committer has committed to.
   *
   * Look at documentation of Graph and Edge before starting this exercise.
   * Your vertices should contain information about the type of node, a 'developer' or a 'repository' node.
   * Edges should only exist between repositories and committers.
   *
   * @param commits RDD containing commit data.
   * @return Graph representation of the commits as described above.
   */
  def assignment_11(commits: RDD[Commit]): Graph[(String, String), String] = {
    // Create vertices for committers
    val committerVertices = commits
      .filter(commit => commit.commit.author.name.nonEmpty)
      .distinct()
      .map(commit => {
        val committerId = md5HashString(commit.commit.author.name)
        (committerId, ("developer", commit.commit.author.name))
      })

    // Create vertices for repositories
    val repositoryVertices = commits
      .distinct()
      .map(commit => {
        val repositoryId = md5HashString(helperCut(commit.url, 5))
        (repositoryId, ("repository", helperCut(commit.url, 5)))
      })

    // Combine all vertices
    val allVertices = committerVertices.union(repositoryVertices)

    // Create edges from committers to repositories
    val committerRepositoryEdges = commits
      .filter(commit => commit.commit.author.name.nonEmpty)
      .map(commit => {
        val committerId = md5HashString(commit.commit.author.name)
        val repositoryId = md5HashString(helperCut(commit.url, 5))
        Edge(committerId, repositoryId, "committed_to")
      })

    // Create reverse edges from repositories to committers
    val repositoryCommitterEdges = committerRepositoryEdges.map(edge => Edge(edge.dstId, edge.srcId, edge.attr))

    // Combine all edges
    val allEdges = committerRepositoryEdges.union(repositoryCommitterEdges)

    // Create and return the graph
    Graph(allVertices, allEdges)
  }
}
