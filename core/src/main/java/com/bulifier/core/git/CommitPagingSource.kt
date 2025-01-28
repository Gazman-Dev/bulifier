package com.bulifier.core.git

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.Date

data class CommitInfo(
    val commitMessage: String,
    val commitDate: Date,
    val commitHash: String
)

class CommitPagingSource(
    private val repoDir: File
) : PagingSource<Int, CommitInfo>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CommitInfo> {
        val page = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            val commits = getCommits(page, pageSize)
            LoadResult.Page(
                data = commits,
                prevKey = if (page == 0) null else page - 1, // Previous page or null if this is the first page
                nextKey = if (commits.isEmpty()) null else page + 1 // Next page or null if no more data
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, CommitInfo>): Int? {
        // Attempt to load the middle page during refresh
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    private suspend fun getCommits(page: Int, pageSize: Int): List<CommitInfo> {
        return withContext(Dispatchers.IO) {
            val commits = mutableListOf<CommitInfo>()

            Git.open(repoDir).use { git ->
                val log = git.log()
                    .setSkip(page * pageSize)
                    .setMaxCount(pageSize)
                    .call()

                for (commit in log) {
                    commits.add(
                        CommitInfo(
                            commitMessage = commit.fullMessage,
                            commitDate = Date(commit.commitTime * 1000L),
                            commitHash = commit.name
                        )
                    )
                }
            }
            commits
        }
    }
}
